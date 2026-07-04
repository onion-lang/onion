package onion.compiler.typing

import onion.compiler.{AST, ClassTable}
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing

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
    case nullable: NullableType =>
      containsTypeVariable(nullable.innerType)
    case w: WildcardType =>
      // A bounded wildcard such as `? extends U` / `? super U` carries a type
      // variable through its bound. Without recursing here, a SAM whose return
      // position is `? extends U` (java.util.function.Function<? super T,
      // ? extends U>: thenApply / Stream.map / Optional.map) was reported as
      // free of type variables, so the closure's expected return type was left
      // as the raw wildcard rather than being inferred, leaking `? extends U`
      // into later checks (issue #259).
      containsTypeVariable(w.upperBound) || w.lowerBound.exists(containsTypeVariable)
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
    // LUB(null, T) where T is a reference type is the *nullable* form T?, not the
    // raw T: the merged value genuinely may be null, so the result type must admit
    // it. Without this, branch merges that mix `null` with a reference type yield a
    // non-null type and spuriously trip the W0012 null-flow warning even when the
    // target is already declared nullable. (NullableType.of collapses T?? and
    // passes through types that can't be wrapped.)
    if (left.isNullType && !right.isNullType && right.isObjectType && !right.isNullable)
      return NullableType.of(right)
    if (right.isNullType && !left.isNullType && left.isObjectType && !left.isNullable)
      return NullableType.of(left)
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

  /**
   * Check whether two types are the same, allowing a primitive type to match
   * its boxed counterpart (e.g. `Int` <-> `java.lang.Integer`).
   */
  def sameOrBoxed(table: ClassTable, a: Type, b: Type): Boolean = {
    if (a eq b) return true
    (a, b) match {
      case (bt: BasicType, ct: ClassType) if bt != BasicType.VOID =>
        Boxing.boxedType(table, bt) eq ct
      case (ct: ClassType, bt: BasicType) if bt != BasicType.VOID =>
        Boxing.boxedType(table, bt) eq ct
      case _ => false
    }
  }

  /**
   * Normalize a type for structural matching: wildcard types collapse to their
   * effective bound (`? super T` -> `T`, `? extends T` -> `T`).
   */
  def effectiveType(tp: Type): Type = tp match {
    case w: WildcardType => w.lowerBound.getOrElse(w.upperBound)
    case other => other
  }

  /**
   * Ensures a term is boolean, unboxing java.lang.Boolean if needed.
   * Reports via reportError when the result is still non-boolean and returns
   * the (possibly unboxed) term either way; callers decide how to recover.
   */
  def ensureBoolean(
    table: ClassTable,
    node: AST.Node,
    term: Term,
    reportError: (AST.Node, Type) => Unit
  ): Term = {
    if (term == null) return null
    val result = Boxing.tryUnboxToBoolean(table, term)
    if (result.`type` != BasicType.BOOLEAN) {
      reportError(node, result.`type`)
    }
    result
  }
}
