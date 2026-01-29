package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Kind.*
import onion.compiler.toolbox.Boxing

/**
 * Handles type checking for addition expressions.
 *
 * Addition in Onion can be:
 * 1. Numeric addition (Int + Int, Double + Double, etc.)
 * 2. String concatenation ("hello" + "world", "count: " + 42)
 *
 * Numeric types are auto-unboxed if needed (Integer + Integer -> int + int).
 * String concatenation auto-boxes primitives and calls toString().
 */
private[typing] class AdditionTyping(
  private val typing: Typing,
  private val body: TypingBodyPass
) {
  import typing.*

  /**
   * Type-check an addition expression.
   * Tries numeric addition first, falls back to string concatenation.
   */
  def typeAddition(node: AST.Addition, context: LocalContext): Option[Term] = {
    val left = body.typed(node.lhs, context).getOrElse(null)
    val right = body.typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return None

    tryNumericAddition(node, left, right).orElse(typeStringConcatenation(node, left, right))
  }

  /**
   * Try to type the expression as numeric addition.
   * Returns None if either operand is not numeric.
   */
  private def tryNumericAddition(node: AST.Addition, left: Term, right: Term): Option[Term] =
    (numericBasicType(left), numericBasicType(right)) match {
      case (Some(leftBasicType), Some(rightBasicType)) =>
        val leftTerm = if (left.isBasicType) left else Boxing.unboxing(table_, left, leftBasicType)
        val rightTerm = if (right.isBasicType) right else Boxing.unboxing(table_, right, rightBasicType)
        Some(body.operatorTyping.processNumericExpression(ADD, node, leftTerm, rightTerm))
      case _ => None
    }

  /**
   * Type the expression as string concatenation.
   * Boxes primitives and calls toString() on both operands, then concat().
   */
  private def typeStringConcatenation(node: AST.Addition, left: Term, right: Term): Option[Term] = {
    val leftBoxed = boxForConcat(node.lhs, left)
    val rightBoxed = boxForConcat(node.rhs, right)
    if (leftBoxed.isEmpty || rightBoxed.isEmpty) return None

    val leftString = toStringCall(node.lhs, leftBoxed.get)
    val rightString = toStringCall(node.rhs, rightBoxed.get)
    // Unwrap NullableType to get the inner type for method lookup
    val leftStringType = leftString.`type` match {
      case nullableType: NullableType => nullableType.innerType.asInstanceOf[ObjectType]
      case other => other.asInstanceOf[ObjectType]
    }
    val concatMethod = body.findMethod(node, leftStringType, "concat", Array[Term](rightString))
    Some(new Call(leftString, concatMethod, Array[Term](rightString)))
  }

  /**
   * Box a primitive type for string concatenation.
   * Returns None for void types (cannot be boxed).
   */
  private def boxForConcat(node: AST.Expression, term: Term): Option[Term] =
    if (!term.isBasicType) Some(term)
    else if (term.`type` == BasicType.VOID) {
      report(SemanticError.IS_NOT_BOXABLE_TYPE, node, term.`type`)
      None
    } else {
      Some(Boxing.boxing(table_, term))
    }

  /**
   * Create a toString() method call on the given term.
   */
  private def toStringCall(node: AST.Expression, term: Term): Term = {
    // Unwrap NullableType to get the inner type for method lookup
    val targetType = term.`type` match {
      case nullableType: NullableType => nullableType.innerType.asInstanceOf[ObjectType]
      case other => other.asInstanceOf[ObjectType]
    }
    val toStringMethod = body.findMethod(node, targetType, "toString")
    new Call(term, toStringMethod, Array.empty)
  }

  /**
   * Extract the numeric BasicType from a term.
   * Handles both primitive types and boxed types (Integer, Long, etc.).
   */
  private def numericBasicType(term: Term): Option[BasicType] = {
    if (term.isBasicType) {
      val basicType = term.`type`.asInstanceOf[BasicType]
      if (isNumeric(basicType)) Some(basicType) else None
    } else {
      Boxing.unboxedType(table_, term.`type`).filter(isNumeric)
    }
  }

  /**
   * Check if a BasicType is numeric (can participate in arithmetic).
   */
  private def isNumeric(basicType: BasicType): Boolean =
    (basicType eq BasicType.BYTE) || (basicType eq BasicType.SHORT) || (basicType eq BasicType.CHAR) ||
      (basicType eq BasicType.INT) || (basicType eq BasicType.LONG) || (basicType eq BasicType.FLOAT) ||
      (basicType eq BasicType.DOUBLE)
}
