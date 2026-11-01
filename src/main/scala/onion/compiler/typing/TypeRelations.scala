package onion.compiler.typing

import onion.compiler.ClassTable
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing

private[typing] object TypeRelations {
  def sameClass(left: ClassType, right: ClassType): Boolean =
    (left eq right) || (left.name == right.name)

  /** A bare, Onion-declared type variable (`[T]`, or `[T extends B?]`): its type
   *  arguments may be nullable, so `null` is a valid `T` and W0012 does not apply
   *  (#2500). `[T extends B]`, and the non-null view after a smart cast, do not. */
  def isBareNullableTypeVariable(tp: Type): Boolean = tp match {
    case tv: TypeVariableType => tv.nullability == Nullability.Nullable
    case _                    => false
  }

  /** A type variable fits an applied type its own bound reaches: `T extends
   *  Comparable[T]` is a `Comparable[T]` (#2338). The bound must be applied (a raw
   *  `T extends Comparable` says nothing about type arguments), and each of the
   *  bound's type arguments must be the very type expected: a variable matches only
   *  itself, never `Object` or another variable. Callers apply it at the top level
   *  only; inside a type-argument comparison arguments stay invariant. */
  def typeVariableFitsAppliedBound(expected: AppliedClassType, tv: TypeVariableType, table: ClassTable): Boolean =
    // An F-bound names the variable through the placeholder frozen while the
    // bounds were resolved, so point that self-reference back at `tv` first.
    TypeSubstitution.substituteType(tv.upperBound, Map(tv.name -> tv), Map.empty, defaultToBound = false) match
      case bound: AppliedClassType =>
        val view =
          if sameClass(expected.raw, bound.raw) then Some(bound)
          else table.appliedViewsOf(bound)(AppliedTypeViews.collectAppliedViewsFrom(bound))
            .collectFirst { case (raw, v) if sameClass(raw, expected.raw) => v }
        view.exists(v => sameTypeArguments(expected.typeArguments, v.typeArguments, table))
      case _ => false

  private def sameTypeArguments(expected: Seq[Type], actual: Seq[Type], table: ClassTable): Boolean =
    expected.length == actual.length && expected.zip(actual).forall { case (e, a) => sameTypeArgument(e, a, table) }

  private def sameTypeArgument(expected: Type, actual: Type, table: ClassTable): Boolean = (expected, actual) match
    case (ev: TypeVariableType, av: TypeVariableType) => ev.widen eq av.widen
    case (_: TypeVariableType, _) | (_, _: TypeVariableType) => false
    // `Comparable[? super T]` and `? extends T`: the wildcard's bound decides, one way,
    // with a variable bound matched by identity as above.
    case (w: WildcardType, _: WildcardType) => false
    case (w: WildcardType, a) =>
      w.lowerBound match
        case Some(lb: TypeVariableType) => a.isInstanceOf[TypeVariableType] && TypeRules.isSuperTypeByIdentity(a, lb)
        case Some(lb) => isAssignableWithBoxing(a, lb, table)
        case None =>
          w.upperBound match
            case ub: TypeVariableType => TypeRules.isSuperTypeByIdentity(ub, a)
            case ub => isAssignableWithBoxing(ub, a, table)
    case (en: NullableType, an: NullableType) => sameTypeArgument(en.innerType, an.innerType, table)
    case (ea: AppliedClassType, aa: AppliedClassType) =>
      sameClass(ea.raw, aa.raw) && sameTypeArguments(ea.typeArguments, aa.typeArguments, table)
    case _ => isAssignableWithBoxing(expected, actual, table, topLevel = false) && isAssignableWithBoxing(actual, expected, table, topLevel = false)

  def isAssignableWithBoxing(expected: Type, actual: Type, table: ClassTable, topLevel: Boolean = true): Boolean =
    if TypeRules.isAssignable(expected, actual) then true
    else if !expected.isBasicType && actual.isBasicType then
      val basicType = actual.asInstanceOf[BasicType]
      basicType != BasicType.VOID && TypeRules.isAssignable(expected, Boxing.boxedType(table, basicType))
    else if expected.isBasicType && !actual.isBasicType then
      val basicType = expected.asInstanceOf[BasicType]
      basicType != BasicType.VOID && TypeRules.isAssignable(Boxing.boxedType(table, basicType), actual)
    else
      (expected, actual) match
        // Strip matching nullable wrappers so a nullable applied generic
        // (e.g. `Node[T]?`) is matched by the invariant AppliedClassType rule
        // below; without this a `Node[T]?` argument did not match a `Node[T]?`
        // parameter, since TypeRules.isAssignable alone cannot relate a
        // type-variable-parameterized applied type to itself (#295).
        case (expNullable: NullableType, actNullable: NullableType) =>
          isAssignableWithBoxing(expNullable.innerType, actNullable.innerType, table, topLevel)
        // `T? ← T`: a non-null value widens to the nullable parameter.
        case (expNullable: NullableType, _) =>
          isAssignableWithBoxing(expNullable.innerType, actual, table, topLevel)
        case (expectedApplied: AppliedClassType, tv: TypeVariableType) if topLevel =>
          typeVariableFitsAppliedBound(expectedApplied, tv, table)
        case (expectedApplied: AppliedClassType, actualApplied: AppliedClassType) =>
          // Type arguments are INVARIANT (generics are erasure-based, no
          // declaration-site variance): Box[Dog] is NOT a Box[Animal], since a
          // mutable Box[Animal] could then store a Cat and corrupt the Box[Dog].
          // Require each argument to be mutually assignable rather than merely a
          // subtype; wildcard variance is still handled by isSuperType above.
          sameClass(expectedApplied.raw, actualApplied.raw) &&
            expectedApplied.typeArguments.length == actualApplied.typeArguments.length &&
            expectedApplied.typeArguments.zip(actualApplied.typeArguments).forall {
              // The wildcard rules of isSuperType above are the whole story: this
              // mutual check would let `Comparator[U]` through for `? super T`,
              // since both sides erase to Object (#2367).
              case (_: WildcardType, _) => false
              case (expArg, actArg) =>
                isAssignableWithBoxing(expArg, actArg, table, topLevel = false) &&
                  isAssignableWithBoxing(actArg, expArg, table, topLevel = false)
            }
        case _ =>
          false
}
