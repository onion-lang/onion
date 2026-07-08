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
   * @param table The class table (used to box a primitive branch to its wrapper)
   * @param node The AST node for error reporting
   * @param left First type
   * @param right Second type
   * @param rootClass The root class type (java.lang.Object)
   * @param reportError Function to report incompatible type errors
   * @return The LUB type, or null if types are incompatible
   */
  def leastUpperBound(
    table: ClassTable,
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
    if (!left.isBasicType && !right.isBasicType) {
      // Neither reference type is a supertype of the other: walk the class
      // hierarchy to the nearest common ancestor instead of collapsing straight
      // to Object. This keeps `if b { new Dog() } else { new Cat() }` typed as
      // their shared superclass `Animal` (and, for interface-only siblings, the
      // single shared interface), so members declared there stay callable.
      (left, right) match {
        case (lc: ClassType, rc: ClassType) =>
          return nearestCommonAncestor(lc, rc, rootClass)
        case (la: ArrayType, ra: ArrayType) if la.dimension == ra.dimension =>
          // LUB of two array types is the array of their component LUB: JVM array
          // covariance makes `Dog[]`/`Cat[]` both assignable to `Animal[]`, so the
          // merged reference is already valid with no conversion (codegen-safe — the
          // element type is only a compile-time refinement of an object reference).
          // Only form it when the component LUB is a reference type; primitive-
          // component arrays (`int[]` vs `long[]`) share no common array supertype,
          // so fall back to Object. A no-op reporter is used for the recursive
          // component LUB so a mismatch falls back silently instead of erroring.
          val compLub = leastUpperBound(table, node, la.component, ra.component, rootClass, (_, _, _) => ())
          if (compLub != null && !compLub.isBasicType)
            return la.table.loadArray(compLub, la.dimension)
          else
            return rootClass
        case _ =>
          return rootClass
      }
    }
    // Exactly one side is a primitive and the other a reference (VOID already
    // handled above). A primitive and a reference share no common type unless the
    // primitive is boxed: box it to its wrapper and let the reference path compute
    // the LUB, so `if b { 1 } else { "s" }` merges to Object (Integer|String) and
    // `if b { 1 } else { someNumber }` merges to Number (Integer|Number), instead
    // of erroring. (issue #308)
    (left, right) match {
      case (lb: BasicType, _) if !right.isBasicType =>
        return leastUpperBound(table, node, Boxing.boxedType(table, lb), right, rootClass, reportError)
      case (_, rb: BasicType) if !left.isBasicType =>
        return leastUpperBound(table, node, left, Boxing.boxedType(table, rb), rootClass, reportError)
      case _ =>
    }
    reportError(node, left, right)
    null
  }

  /**
   * Nearest common ancestor of two class types when neither is a supertype of
   * the other. Prefers a common SUPERCLASS (walking `left`'s superclass chain
   * and returning the first ancestor that is also a supertype of `right`); if
   * that only reaches Object but there is exactly one shared interface, returns
   * that interface. When the choice is ambiguous (Onion has no intersection
   * types), falls back to `rootClass` (Object) — the previous behaviour.
   *
   * Whatever is returned is a genuine supertype of BOTH operands: superclass
   * candidates come from `left`'s own chain and are checked against `right`;
   * interface candidates are implemented by `left` and checked against `right`.
   */
  private def nearestCommonAncestor(
    left: ClassType,
    right: ClassType,
    rootClass: ClassType
  ): Type = {
    // Walk left's proper superclass chain (excluding Object) for the first
    // ancestor that is also a supertype of right.
    var superCandidate: ClassType = left.superClass
    while (superCandidate != null) {
      if ((superCandidate ne rootClass) &&
          (superCandidate.name != "java.lang.Object") &&
          TypeRules.isSuperType(superCandidate, right)) {
        return superCandidate
      }
      superCandidate = superCandidate.superClass
    }
    // No common superclass below Object. Look for shared interfaces (transitive)
    // implemented by left that are supertypes of right. A unique winner is the
    // LUB; multiple unrelated ones have no unique LUB without intersection
    // types, so fall back to Object.
    val commonIfaces = collectInterfaces(left).filter { iface =>
      (iface ne rootClass) && TypeRules.isSuperType(iface, right)
    }
    // Keep only the most specific interfaces (drop any that are a supertype of
    // another candidate), so `Speaker` wins even if it also extends a broader
    // interface both share.
    val mostSpecific = commonIfaces.filter { c =>
      !commonIfaces.exists(o => (o ne c) && TypeRules.isSuperType(c, o))
    }
    mostSpecific match {
      case Seq(single) => single
      case _           => rootClass
    }
  }

  /**
   * All interfaces (directly declared and transitively inherited, including
   * super-interfaces reached via superclasses) implemented by a class type.
   */
  private def collectInterfaces(tp: ClassType): List[ClassType] = {
    val seen = scala.collection.mutable.LinkedHashMap.empty[String, ClassType]
    def visitClass(c: ClassType): Unit = {
      if (c == null) return
      c.interfaces.foreach(visitInterface)
      visitClass(c.superClass)
    }
    def visitInterface(i: ClassType): Unit = {
      if (i == null || seen.contains(i.name)) return
      seen(i.name) = i
      i.interfaces.foreach(visitInterface)
    }
    visitClass(tp)
    seen.values.toList
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
