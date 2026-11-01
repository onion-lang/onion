package onion.compiler.typing

import onion.compiler.AST

/**
 * Decides, from the syntax alone, whether a `history` clause is a preorder (RFC #2557, design
 * 3.3). A history relates two states of one object; it must be reflexive (`h(a, a)`) and
 * transitive, or checking one call at a time would not check the whole life of the object.
 *
 * Only the common shapes are classified. Anything else is [[Unknown]], which is left to the
 * prover (a later stage) and is never reported here.
 */
private[typing] object HistoryPreorder {
  sealed trait Verdict
  case object Preorder extends Verdict
  /** `reason` is the offending relation as source text, the same in every language. */
  final case class NotPreorder(reason: String) extends Verdict
  case object Unknown extends Verdict

  def classify(e: AST.Expression): Verdict = e match {
    case AST.LogicalAnd(_, l, r) => combine(classify(l), classify(r))
    case AST.GreaterOrEqual(_, l, r) => relation(">=", l, r)
    case AST.LessOrEqual(_, l, r) => relation("<=", l, r)
    case AST.Equal(_, l, r) => relation("==", l, r)
    case AST.GreaterThan(_, l, r) => strict(">", l, r)
    case AST.LessThan(_, l, r) => strict("<", l, r)
    case AST.NotEqual(_, l, r) => strict("!=", l, r)
    case _ => Unknown
  }

  private def combine(a: Verdict, b: Verdict): Verdict = (a, b) match {
    case (n: NotPreorder, _) => n
    case (_, n: NotPreorder) => n
    case (Preorder, Preorder) => Preorder
    case _ => Unknown
  }

  /** `x OP old(x)` or `old(x) OP x`, with an optional constant offset on the old side. */
  private def relation(op: String, l: AST.Expression, r: AST.Expression): Verdict =
    (shape(l), shape(r)) match {
      case (Some(Now(a)), Some(Old(b, off))) if a == b => offsetVerdict(op, a, off)
      case (Some(Old(b, off)), Some(Now(a))) if a == b => offsetVerdict(op, a, off)
      case _ if mentionsOld(l) || mentionsOld(r) => Unknown
      case _ => Preorder // a predicate on the new state only
    }

  /** `>`, `<`, `!=` against the old value are irreflexive: `h(a, a)` is false. */
  private def strict(op: String, l: AST.Expression, r: AST.Expression): Verdict =
    (shape(l), shape(r)) match {
      case (Some(Now(a)), Some(Old(b, _))) if a == b => NotPreorder(s"$a $op old($a)")
      case (Some(Old(b, _)), Some(Now(a))) if a == b => NotPreorder(s"old($a) $op $a")
      case _ if mentionsOld(l) || mentionsOld(r) => Unknown
      case _ => Preorder
    }

  /** A constant offset breaks reflexivity (`n >= old(n) + 1`) or transitivity (`n >= old(n) - 1`). */
  private def offsetVerdict(op: String, field: String, offset: Option[Int]): Verdict = offset match {
    case None | Some(0) => Preorder
    case Some(k) => NotPreorder(s"$field $op old($field) ${if (k > 0) "+ " + k else "- " + (-k)}")
  }

  private sealed trait Shape
  private final case class Now(field: String) extends Shape
  /** `old(field)` shifted by `offset`; `None` means no shift. */
  private final case class Old(field: String, offset: Option[Int]) extends Shape

  private def shape(e: AST.Expression): Option[Shape] = e match {
    case AST.UnqualifiedMethodCall(_, "old", List(arg), _) => fieldName(arg).map(f => Old(f, None))
    case AST.Addition(_, o, AST.IntegerLiteral(_, k, _)) => shape(o).collect { case Old(f, None) => Old(f, Some(k)) }
    case AST.Subtraction(_, o, AST.IntegerLiteral(_, k, _)) => shape(o).collect { case Old(f, None) => Old(f, Some(-k)) }
    case _ => fieldName(e).map(Now.apply)
  }

  private def fieldName(e: AST.Expression): Option[String] = e match {
    case AST.Id(_, n) => Some(n)
    case AST.UnqualifiedFieldReference(_, n) => Some(n)
    case AST.MemberSelection(_, _: AST.CurrentInstance, n) => Some(n)
    case _ => None
  }

  private def mentionsOld(e: AST.Expression): Boolean = e match {
    case AST.UnqualifiedMethodCall(_, "old", _, _) => true
    case b: AST.BinaryExpression => mentionsOld(b.lhs) || mentionsOld(b.rhs)
    case _ => false
  }
}
