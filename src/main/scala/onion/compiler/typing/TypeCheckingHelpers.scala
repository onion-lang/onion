package onion.compiler.typing

import onion.compiler.AST
import onion.compiler.TypedAST.*

/**
 * Common type checking utility functions shared across typing components.
 *
 * These functions are pure utilities that don't depend on typing state,
 * extracted to eliminate duplication across ClosureTyping, ControlExpressionTyping,
 * and TypingBodyPass.
 */
private[typing] object TypeCheckingHelpers {

  /**
   * Recursively check if a type contains any type variables.
   * Used for generic type inference and structural type matching.
   */
  def containsTypeVariable(typ: Type): Boolean = typ match {
    case _: TypeVariableType => true
    case applied: AppliedClassType =>
      applied.typeArguments.exists(containsTypeVariable)
    case array: ArrayType =>
      containsTypeVariable(array.component)
    case _ => false
  }

  /**
   * Compute the least upper bound (LUB) of two types.
   *
   * @param node The AST node for error reporting
   * @param left First type
   * @param right Second type
   * @param rootClass The root class type (java.lang.Object)
   * @param reportError Function to report incompatible type errors
   * @return The LUB type, or null if types are incompatible
   */
  def leastUpperBound(
    node: AST.Node,
    left: Type,
    right: Type,
    rootClass: ClassType,
    reportError: (AST.Node, Type, Type) => Unit
  ): Type = {
    if (left == null || right == null) return null
    if (left.isBottomType) return right
    if (right.isBottomType) return left
    if (left eq right) return left
    if (left.isNullType && right.isNullType) return left
    if ((left eq BasicType.VOID) || (right eq BasicType.VOID)) {
      if ((left eq BasicType.VOID) && (right eq BasicType.VOID)) return BasicType.VOID
      reportError(node, left, right)
      return null
    }
    if (TypeRules.isSuperType(left, right)) return left
    if (TypeRules.isSuperType(right, left)) return right
    if (!left.isBasicType && !right.isBasicType) return rootClass
    reportError(node, left, right)
    null
  }

  /**
   * Check if two type arrays are identical by reference equality.
   */
  def sameTypes(left: Array[Type], right: Array[Type]): Boolean = {
    if (left.length != right.length) return false
    left.indices.forall(i => left(i) eq right(i))
  }
}
