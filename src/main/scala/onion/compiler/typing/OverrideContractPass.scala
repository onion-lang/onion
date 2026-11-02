package onion.compiler.typing

import onion.compiler.{AST, WarningCategory}
import onion.compiler.contracts.{EffectiveContract, HistoryProver}

/**
 * Pass 9 of typing (RFC #2557, design 4): an override's body against the effective contract.
 *
 * A caller may use a subtype's method wherever the supertype's can be used, so the body of an
 * override must keep the effective postcondition `post_eff` whenever the effective precondition
 * `pre_eff` holds at entry (`pre_eff = pre_super or pre_sub`). The solver is asked whether it can
 * fail. A counterexample is a warning (W0025), not an error, for the same reason as W0021: it may
 * rest on a wrap-around the program never reaches. A body the encoder does not model reports nothing.
 */
final class OverrideContractPass(report: (String, onion.compiler.Location, String) => Unit) {
  def run(units: Seq[AST.CompilationUnit]): Unit = {
    val classes: Map[String, (AST.ClassDeclaration, AST.CompilationUnit)] =
      units.flatMap(u => u.toplevels.collect { case c: AST.ClassDeclaration => c.name -> ((c, u)) }).toMap
    for ((_, (cls, unit)) <- classes) {
      val supers = superChain(cls, classes.view.mapValues(_._1).toMap)
      if (supers.nonEmpty) checkClass(cls, supers, unit)
    }
  }

  private def superChain(cls: AST.ClassDeclaration, classes: Map[String, AST.ClassDeclaration]): Seq[AST.ClassDeclaration] = {
    @annotation.tailrec
    def go(c: AST.ClassDeclaration, seen: Seq[AST.ClassDeclaration]): Seq[AST.ClassDeclaration] =
      Option(c.superClass).map(_.desc).collect { case AST.ReferenceType(n, _) => n }
        .flatMap(classes.get).filterNot(seen.contains) match {
        case Some(parent) => go(parent, seen :+ parent)
        case None         => seen
      }
    go(cls, Seq.empty)
  }

  private def methodsOf(c: AST.ClassDeclaration): Seq[AST.MethodDeclaration] =
    (c.defaultSection.toSeq ++ c.sections).flatMap(_.members).collect { case m: AST.MethodDeclaration => m }

  private def isInt(t: AST.TypeNode): Boolean = t != null && t.desc == AST.PrimitiveType(AST.KInt)

  private def checkClass(cls: AST.ClassDeclaration, supers: Seq[AST.ClassDeclaration], unit: AST.CompilationUnit): Unit = {
    val chain = cls +: supers
    val intFields = chain.flatMap(c => (c.defaultSection.toSeq ++ c.sections).flatMap(_.members)).collect {
      case f: AST.FieldDeclaration if isInt(f.typeRef) => f.name
    }.toSet
    val invariants = chain.flatMap(_.invariants)
    val callees = chain.reverse.flatMap(methodsOf).map(m => m.name -> m).toMap
    for {
      sub <- methodsOf(cls) if sub.block != null && (sub.requires.nonEmpty || sub.ensures.nonEmpty)
      sup <- supers.flatMap(methodsOf).find(p => p.name == sub.name && p.args.size == sub.args.size)
      if sup.requires.nonEmpty || sup.ensures.nonEmpty
    } {
      val eff = EffectiveContract.of(
        EffectiveContract.Clauses(sup.requires, sup.ensures),
        EffectiveContract.Clauses(sub.requires, sub.ensures))
      eff.post.foreach { post =>
        // the body runs under pre_eff (and the invariants); the caller's own `requires` is replaced by it
        val assumed = invariants ++ eff.pre.toList
        val asMethod = sub.copy(requires = Nil)
        HistoryProver.proveEnsures(asMethod, List(post), assumed, intFields, callees) match {
          case HistoryProver.Counterexample(before) =>
            val shown = before.toSeq.sortBy(_._1).map { case (k, v) => s"$k = $v" }.mkString(", ")
            report(unit.sourceFile, sub.location,
              s"'${sub.name}' can break the contract it inherits: for example when $shown. " +
                "Its body must keep the effective postcondition whenever the effective precondition held; " +
                "strengthen its requires, or weaken what it promises")
          case _ =>
        }
      }
    }
  }
}
