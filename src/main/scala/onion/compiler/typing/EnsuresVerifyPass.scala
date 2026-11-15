package onion.compiler.typing

import onion.compiler.{AST, Location}
import onion.compiler.contracts.HistoryProver

/**
 * Pass 10 of typing (RFC #2557, design 6 and 7.3): the `ensures` of a method, proved.
 *
 * For each method that states an `ensures`, the solver is asked whether the body can end with the
 * clause false, given the class `invariant`s and the method's own `requires`. The answer is one of
 *
 *  - proved: `proved` is told (`--verbose` lists it);
 *  - a counterexample: `report` is told, with the values;
 *  - not modelled: nothing is said yet. Once an unproved contract is an error, this is where it is
 *    reported, with the form the prover does not model.
 *
 * A `@Trusted` method is not checked: its contract is relied on, and listed by the warning W0027.
 */
final class EnsuresVerifyPass(
  report: (String, Location, String) => Unit,
  proved: String => Unit,
  onProved: (String, String, Location) => Unit = (_, _, _) => ()
) {
  def run(units: Seq[AST.CompilationUnit]): Unit = {
    val classes: Map[String, (AST.ClassDeclaration, AST.CompilationUnit)] =
      units.flatMap(u => u.toplevels.collect { case c: AST.ClassDeclaration => c.name -> ((c, u)) }).toMap
    val byName = classes.view.mapValues(_._1).toMap
    for ((_, (cls, unit)) <- classes) check(cls, chainOf(cls, byName), unit)
  }

  private def chainOf(cls: AST.ClassDeclaration, classes: Map[String, AST.ClassDeclaration]): Seq[AST.ClassDeclaration] = {
    @annotation.tailrec
    def go(c: AST.ClassDeclaration, seen: Seq[AST.ClassDeclaration]): Seq[AST.ClassDeclaration] =
      ContractSupertypes.name(c.superClass)
        .flatMap(classes.get).filterNot(seen.contains) match {
        case Some(parent) => go(parent, seen :+ parent)
        case None         => seen
      }
    go(cls, Seq(cls))
  }

  private def methodsOf(c: AST.ClassDeclaration): Seq[AST.MethodDeclaration] =
    (c.defaultSection.toSeq ++ c.sections).flatMap(_.members).collect { case m: AST.MethodDeclaration => m }

  private def isInt(t: AST.TypeNode): Boolean = t != null && t.desc == AST.PrimitiveType(AST.KInt)

  private def check(cls: AST.ClassDeclaration, chain: Seq[AST.ClassDeclaration], unit: AST.CompilationUnit): Unit = {
    val intFields = chain.flatMap(c => (c.defaultSection.toSeq ++ c.sections).flatMap(_.members)).collect {
      case f: AST.FieldDeclaration if isInt(f.typeRef) => f.name
    }.toSet
    val invariants = chain.flatMap(_.invariants)
    val callees = chain.reverse.flatMap(methodsOf).map(m => m.name -> m).toMap
    for (m <- methodsOf(cls) if m.block != null && m.ensures.nonEmpty && !m.annotations.exists(_.name == "Trusted")) {
      HistoryProver.proveEnsures(m, m.ensures, invariants, intFields, callees) match {
        // A proof may lean on a class invariant only once something checks that invariant (#2602,
        // #2672): a constructor can break it unseen. A proof that needs one is not "proved".
        case HistoryProver.Proved if invariants.nonEmpty &&
            HistoryProver.proveEnsures(m, m.ensures, Nil, intFields, callees) != HistoryProver.Proved =>
        case HistoryProver.Proved =>
          onProved(cls.name, m.name, m.location)
          proved(s"${cls.name}.${m.name}: ${m.ensures.map(onion.compiler.contracts.ExpressionText.of).mkString(" && ")}")
        case HistoryProver.Counterexample(before) =>
          val shown = before.toSeq.sortBy(_._1).map { case (k, v) => s"$k = $v" }.mkString(", ")
          report(unit.sourceFile, m.location,
            s"'${m.name}' can break its ensures: for example when $shown. " +
              "Strengthen its requires or an invariant, weaken the ensures, or fix the body")
        case HistoryProver.Unsupported =>
      }
    }
  }
}
