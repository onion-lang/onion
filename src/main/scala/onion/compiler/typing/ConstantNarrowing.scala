package onion.compiler.typing

import onion.compiler.TypedAST.*

/**
 * Constant narrowing: an integer literal (or its negation) that fits a
 * narrow integral target's range target-types to Byte/Short/Char, mirroring
 * Java's `byte b = 100`. Shared between plain assignment (`AssignabilitySupport`),
 * constructor overload resolution (`ConstructionTyping`), and method overload
 * resolution (`MethodResolutionSupport`), which all need to recognize the same
 * literals.
 */
private[typing] object ConstantNarrowing {

  /** The compile-time int value of an integer literal or its unary negation. */
  def constantIntOf(term: Term): Option[Int] = term match {
    case iv: IntValue => Some(iv.value)
    case u: UnaryTerm if u.kind == UnaryTerm.Kind.MINUS =>
      u.operand match { case iv: IntValue => Some(-iv.value); case _ => None }
    case _ => None
  }

  /** Whether an integer value fits the range of a narrow integral target. */
  def fits(bt: BasicType, value: Int): Boolean = bt match {
    case BasicType.BYTE  => value >= -128 && value <= 127
    case BasicType.SHORT => value >= -32768 && value <= 32767
    case BasicType.CHAR  => value >= 0 && value <= 65535
    case _ => false
  }
}
