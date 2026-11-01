package onion.compiler.typing

import onion.compiler.{Location, SemanticError, Typing}
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
        check(clause, md.location, classes, unitEffects, table)
      for (cd <- typesWithContracts; clause <- cd.invariants ++ cd.histories)
        check(clause, cd.location, classes, unitEffects, table)
    }
  }

  private def check(clause: Term, ownerLocation: Location, classes: Seq[ClassDefinition],
                    unitEffects: Map[EffectInference.Unit0, Set[Effect]], table: EffectTable.Table): Unit = {
    val (effects, sites) = EffectInference.termEffects(clause, classes, unitEffects, table)
    if (effects.nonEmpty) {
      // Name the first call that has an effect; a bare write has no call to name.
      val culprit = sites.find(_.effects.nonEmpty)
      val callee = culprit.map(_.callee).getOrElse("an assignment")
      val why = describe(culprit.map(_.effects).getOrElse(effects))
      val where = culprit.flatMap(c => Option(c.location)).orElse(Option(clause.location)).getOrElse(ownerLocation)
      typing.report(SemanticError.CONTRACT_NOT_PURE, where, callee, why)
    }
  }

  /** What the effects mean for a reader, completing "calls X, which ...". */
  private def describe(effects: Set[Effect]): String = {
    val world = effects - Effect.Mutate
    val parts = Seq(
      if (world.nonEmpty) Some(s"has the effect ${world.toSeq.sorted.map(_.name).mkString(", ")}") else None,
      if (effects.contains(Effect.Mutate)) Some("writes to memory") else None
    ).flatten
    parts.mkString(" and ") + (if (world.contains(Effect.Unknown)) " (it is not in the effect table)" else "")
  }
}
