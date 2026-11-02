package onion.compiler.contracts

import onion.compiler.{AST, Location}

/**
 * The effective contract of an override (RFC #2557, design 4; Liskov and Wing, 1994).
 *
 * A subtype's method may be called wherever the supertype's can, so what a caller may rely on is
 * built from both contracts by construction, and the methods rule holds without a proof:
 *
 * {{{
 * pre_eff  = pre_super  or pre_sub          -- weaker: it accepts what either accepts
 * post_eff = (old(pre_super) => post_super) and (old(pre_sub) => post_sub)   -- stronger
 * }}}
 *
 * so `pre_super => pre_eff`, and `post_eff` implies `post_super` wherever `pre_super` held.
 * Clauses are conjunctions: an absent `requires` is `true`, an absent `ensures` is `true`.
 */
object EffectiveContract {
  /** A contract as lists of clauses; the clauses of one list are conjoined. */
  final case class Clauses(requires: List[AST.Expression], ensures: List[AST.Expression])

  /** The effective contract of `sub` overriding `sup`, as single expressions (`None` means `true`). */
  final case class Effective(pre: Option[AST.Expression], post: Option[AST.Expression])

  def of(sup: Clauses, sub: Clauses): Effective = {
    val loc = (sup.requires ++ sup.ensures ++ sub.requires ++ sub.ensures).headOption.map(_.location).orNull
    val preSuper = conjunction(sup.requires, loc)
    val preSub = conjunction(sub.requires, loc)
    // An absent precondition on either side is `true`, so the disjunction is `true`: no clause.
    val pre = (preSuper, preSub) match {
      case (Some(a), Some(b)) => Some(AST.LogicalOr(loc, a, b))
      case _                  => None
    }
    val postSuper = conjunction(sup.ensures, loc).map(implies(preSuper, _, loc))
    val postSub = conjunction(sub.ensures, loc).map(implies(preSub, _, loc))
    val post = (postSuper, postSub) match {
      case (Some(a), Some(b)) => Some(AST.LogicalAnd(loc, a, b))
      case (a, b)             => a.orElse(b)
    }
    Effective(pre, post)
  }

  /** `a and b and ...`, or `None` for no clause (that is, `true`). */
  private def conjunction(clauses: List[AST.Expression], loc: Location): Option[AST.Expression] =
    clauses.reduceLeftOption((a, b) => AST.LogicalAnd(loc, a, b))

  /** `old(pre) => post`, written `!old(pre) || post`; with no precondition it is just `post`. */
  private def implies(pre: Option[AST.Expression], post: AST.Expression, loc: Location): AST.Expression =
    pre match {
      case Some(p) => AST.LogicalOr(loc, AST.Not(loc, oldOf(p, loc)), post)
      case None    => post
    }

  private def oldOf(e: AST.Expression, loc: Location): AST.Expression =
    AST.UnqualifiedMethodCall(loc, "old", List(e))
}
