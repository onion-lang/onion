package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Kind.*
import onion.compiler.toolbox.Boxing
import onion.compiler.typing.session.TypingBodyContext

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
  private val bodyContext: TypingBodyContext,
  private val body: TypingBodyPass
) {
  /**
   * Type-check an addition expression.
   * Tries numeric addition first, falls back to string concatenation.
   */
  def typeAddition(node: AST.Addition, context: LocalContext): Option[Term] = {
    val left = body.typed(node.lhs, context).getOrElse(null)
    val right = body.typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return None

    // Operator overloading for '+': numeric first, then a user-defined
    // plus() when neither side is a String (String keeps concatenation),
    // and concatenation as the final fallback
    tryNumericAddition(node, left, right)
      .orElse(tryPlusOperator(node, left, right))
      .orElse(typeStringConcatenation(node, left, right))
  }

  private def tryPlusOperator(node: AST.Addition, left: Term, right: Term): Option[Term] = {
    def isString(t: Term): Boolean = t.`type` match {
      case ct: ObjectType => ct.name == "java.lang.String"
      case n: NullableType => n.innerType.name == "java.lang.String"
      case _ => false
    }
    if (left.`type`.isObjectType && !isString(left) && !isString(right))
      body.operatorTyping.tryOperatorMethod(node, "+", left, right)
    else None
  }

  /**
   * Try to type the expression as numeric addition.
   * Returns None if either operand is not numeric.
   */
  private def tryNumericAddition(node: AST.Addition, left: Term, right: Term): Option[Term] =
    (numericBasicType(left), numericBasicType(right)) match {
      case (Some(leftBasicType), Some(rightBasicType)) =>
        val leftTerm = if (left.isBasicType) left else Boxing.unboxing(bodyContext.table, left, leftBasicType)
        val rightTerm = if (right.isBasicType) right else Boxing.unboxing(bodyContext.table, right, rightBasicType)
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
      bodyContext.report(SemanticError.IS_NOT_BOXABLE_TYPE, node, term.`type`)
      None
    } else {
      Some(Boxing.boxing(bodyContext.table, term))
    }

  /**
   * Convert a term to String via String.valueOf(Object), which matches Java's
   * concatenation semantics for null ("a" + null == "anull") and never NPEs.
   */
  private def toStringCall(node: AST.Expression, term: Term): Term = {
    val stringType = bodyContext.load("java.lang.String")
    val arg: Term = new AsInstanceOf(term, bodyContext.rootClass)
    val valueOfMethods = stringType.findMethod("valueOf", Array[Term](arg))
    // String.valueOf(Object) always exists on the JDK
    new CallStatic(stringType, valueOfMethods(0), Array[Term](arg))
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
      Boxing.unboxedType(bodyContext.table, term.`type`).filter(isNumeric)
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
