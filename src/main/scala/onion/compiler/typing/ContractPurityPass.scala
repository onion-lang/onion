package onion.compiler.typing

import onion.compiler.{AST, Location, SemanticError, Typing}
import onion.compiler.TypedAST.*
import onion.compiler.effects.{Effect, EffectInference, EffectTable}

/**
 * Pass 6 of typing (RFC #2557): a contract expression must be pure.
 *
 * A contract states what holds; it may not change anything. So a clause may call only what
 * the effect inference can vouch for as having no effect at all, `mutate` included: a call
 * that does I/O, writes memory, or is simply unknown makes the contract E0106.
 *
 * It runs after every unit is typed because effect inference is cross-unit: a clause that
 * calls a method of the program needs that method's effects, and that method's body may be
 * typed after the clause was.
 */
final class ContractPurityPass(typing: Typing) {

  def run(classes: Seq[ClassDefinition]): Unit = {
    val withContracts: Seq[MethodDefinition] = for {
      cd <- classes
      m  <- cd.methods.toSeq
      md <- Some(m).collect { case d: MethodDefinition => d }
      if md.preconditions.nonEmpty || md.postconditions.nonEmpty
    } yield md
    val typesWithContracts: Seq[ClassDefinition] = classes.filter(c => c.invariants.nonEmpty || c.histories.nonEmpty)
    if (withContracts.nonEmpty || typesWithContracts.nonEmpty) {
      val table = typing.effectTable
      val (_, unitEffects) = EffectInference.inferUnits(classes, table)
      for (md <- withContracts; clause <- md.preconditions ++ md.postconditions)
        check(clause, md.location, classes, unitEffects, table, md.affiliation match { case c: ClassDefinition => c; case _ => null })
      for (cd <- typesWithContracts; clause <- cd.invariants ++ cd.histories)
        check(clause, cd.location, classes, unitEffects, table, cd)
    }
  }

  private def check(clause: Term, ownerLocation: Location, classes: Seq[ClassDefinition],
                    unitEffects: Map[EffectInference.Unit0, Set[Effect]], table: EffectTable.Table,
                    owner: ClassDefinition): Unit = {
    // A clause runs inside its own class, so that class's static initializer has already run.
    val (effects, sites) = EffectInference.termEffects(clause, classes, unitEffects, table, owner)
    if (effects.nonEmpty) {
      // Name the first call that has an effect; a bare write has no call to name.
      val culprit = sites.find(_.effects.nonEmpty)
      val callee = culprit.map(_.callee).getOrElse("=")
      val blamed = culprit.map(_.effects).getOrElse(effects)
      // Typed calls mostly carry no location, so point at the call in the clause's source that
      // has the culprit's name; else at the clause itself; never at the owning `def` or `class`
      // while the clause is known (#2605).
      val source = Option(typing.contractClauseSources.get(clause))
      val where = culprit.flatMap(c => source.flatMap(callNamed(_, methodName(c.callee))))
        .orElse(culprit.flatMap(c => Option(c.location)))
        .orElse(Option(clause.location))
        .orElse(source.flatMap(s => Option(s.location)))
        .getOrElse(ownerLocation)
      // The reason is a kind and the effect names (identifiers, the same in every language); the
      // sentence is built from a message key, so it is translated with the rest of the message.
      typing.report(SemanticError.CONTRACT_NOT_PURE, where, callee, reasonKind(blamed), worldEffects(blamed))
    }
  }

  /** The method a call site names: `add` for `java.util.List.add` or `K::add`, `f` for a top-level `f`. */
  private def methodName(callee: String): String = {
    val i = callee.lastIndexOf("::")
    if (i >= 0) callee.substring(i + 2)
    else if (callee.startsWith("new ")) callee
    else callee.substring(callee.lastIndexOf('.') + 1)
  }

  /** The location of the first call to `name` in a clause's source, left to right. */
  private def callNamed(node: Any, name: String): Option[Location] = node match {
    case c: AST.MethodCall if c.name == name && c.location != null            => Some(c.location)
    case c: AST.SafeMethodCall if c.name == name && c.location != null        => Some(c.location)
    case c: AST.UnqualifiedMethodCall if c.name == name && c.location != null => Some(c.location)
    case c: AST.StaticMethodCall if c.name == name && c.location != null      => Some(c.location)
    case c: AST.SuperMethodCall if c.name == name && c.location != null       => Some(c.location)
    case p: Product => p.productIterator.map(callNamed(_, name)).collectFirst { case Some(l) => l }
    case it: Iterable[?] => it.iterator.map(callNamed(_, name)).collectFirst { case Some(l) => l }
    case _ => None
  }

  /** `memory` (writes only), `world` (an outside effect only), `both`, or `unknown` (not in the table). */
  private def reasonKind(effects: Set[Effect]): String = {
    val world = effects - Effect.Mutate
    if (world.contains(Effect.Unknown)) "unknown"
    else if (world.nonEmpty && effects.contains(Effect.Mutate)) "both"
    else if (world.nonEmpty) "world"
    else "memory"
  }

  /** The names of the outside effects, e.g. `console, read`; empty when the call only writes memory. */
  private def worldEffects(effects: Set[Effect]): String =
    (effects - Effect.Mutate - Effect.Unknown).toSeq.sorted.map(_.name).mkString(", ")
}
