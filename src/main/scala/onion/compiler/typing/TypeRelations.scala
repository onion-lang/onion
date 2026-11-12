package onion.compiler.typing

import onion.compiler.ClassTable
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing

private[typing] object TypeRelations {
  def sameClass(left: ClassType, right: ClassType): Boolean =
    (left eq right) || (left.name == right.name)

  /** `expected` is a supertype of the box `boxed`. The box is a raw class, which an applied
   *  type accepts by erasure, so an applied `expected` is matched against the box's own view
   *  of that class: `Comparable[JInteger]` fits `Integer`, `Comparable[String]` does not
   *  (#2561, #2594). A nullable type argument is matched by its non-null form:
   *  `Comparable[JInteger?]` fits `Integer`, as a `JInteger` value already does. */
  def boxSupertypeAccepts(table: ClassTable, expected: ClassType, boxed: ClassType): Boolean = expected match {
    case applied: AppliedClassType =>
      val views = table.appliedViewsOf(boxed)(AppliedTypeViews.collectAppliedViewsFrom(boxed))
      lazy val nonNullArgs = AppliedClassType(applied.raw, applied.typeArguments.toList.map {
        case n: NullableType => n.innerType
        case t => t
      })
      views.exists { case (raw, view) =>
        sameClass(raw, applied.raw) &&
          (TypeRules.isSuperType(applied, view) || TypeRules.isSuperType(nonNullArgs, view))
      }
    case _ => TypeRules.isSuperType(expected, boxed)
  }

  /** A plain class's own view of an interface fixes its type arguments: `Integer` is a
   *  `Comparable[Integer]` and nothing else, and no subclass can implement `Comparable`
   *  a second time. So an applied `expected` whose erasure the class reaches, but whose
   *  arguments its view contradicts (`Comparable[String]` for an `Integer` or a `JInteger`
   *  value), can hold no value of it (#2649). Only a concrete view decides: a raw generic
   *  class, a type variable on either side, or the class itself leave the old rule alone. */
  def ownViewRejects(table: ClassTable, expected: Type, actual: Type): Boolean = (expected, actual) match
    case (e: NullableType, a: NullableType) => ownViewRejects(table, e.innerType, a.innerType)
    case (e: NullableType, _) => ownViewRejects(table, e.innerType, actual)
    case (_, a: NullableType) => ownViewRejects(table, expected, a.innerType)
    case (_, _: AppliedClassType) | (_, _: TypeVariableType) => false
    case (e: AppliedClassType, a: ClassType)
        if !sameClass(e.raw, a) && !TypeCheckingHelpers.containsTypeVariable(e) =>
      val views = table.appliedViewsOf(a)(AppliedTypeViews.collectAppliedViewsFrom(a))
        .collect { case (raw, view) if sameClass(raw, e.raw) => view }
      views.nonEmpty && views.forall(v => !TypeCheckingHelpers.containsTypeVariable(v)) &&
        !boxSupertypeAccepts(table, e, a) && views.forall(v => argumentsDisjoint(e, v))
    case _ => false

  /** Some type argument of `expected` and of the class's own `view` can share no value:
   *  `Comparable[String]` against `Comparable[Integer]`. Related arguments
   *  (`Iterable[Object]` against `Iterable[String]`, `Map[String, String]` against
   *  `Properties`' `Map[Object, Object]`) are left to the old rule: such a value can
   *  still work at run time, so rejecting it would be a break, not a fix (#2649). */
  private def argumentsDisjoint(expected: AppliedClassType, view: AppliedClassType): Boolean =
    expected.typeArguments.length == view.typeArguments.length &&
      expected.typeArguments.indices.exists(i => provablyDisjoint(expected.typeArguments(i), view.typeArguments(i)))

  private def provablyDisjoint(left: Type, right: Type): Boolean =
    def core(t: Type): Type = t match
      case n: NullableType => n.innerType
      case other => other
    def erased(c: ClassType): ClassType = c match
      case ap: AppliedClassType => ap.raw
      case other => other
    (core(left), core(right)) match
      case (_: TypeVariableType, _) | (_, _: TypeVariableType) => false
      case (l: ClassType, r: ClassType) =>
        val (le, re) = (erased(l), erased(r))
        // A non-final class and an interface can meet in a subclass (JLS 5.5.1).
        def mayMeet(x: ClassType, y: ClassType): Boolean =
          x.isInterface && !onion.compiler.Modifier.isFinal(y.modifier)
        !TypeRules.isSuperType(le, re) && !TypeRules.isSuperType(re, le) && !mayMeet(le, re) && !mayMeet(re, le)
      case _ => false

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

  /** Constructor resolution's boxing-aware match: as `isAssignableWithBoxing`, except
    * that `null` never fills a primitive slot. Boxing the slot's type would let it
    * through for a `new`, an `extends A(..)` and a `: this(..)` alike, and nothing
    * after constructor resolution rejects it (#2828). Method calls keep the plain
    * check: their argument check reports E0000 at the `null` itself. */
  def isConstructorArgAssignable(expected: Type, actual: Type, table: ClassTable): Boolean =
    !(expected.isBasicType && actual.isNullType) && isAssignableWithBoxing(expected, actual, table)

  def isAssignableWithBoxing(expected: Type, actual: Type, table: ClassTable, topLevel: Boolean = true): Boolean =
    if ownViewRejects(table, expected, actual) then false
    else if TypeRules.isAssignable(expected, actual) then true
    else if !expected.isBasicType && actual.isBasicType then
      val basicType = actual.asInstanceOf[BasicType]
      basicType != BasicType.VOID && {
        val boxed = Boxing.boxedType(table, basicType)
        // An applied target is matched against the box's own view, not by erasure:
        // `val y: Comparable[String] = 5` is rejected (#2594).
        // A nullable target (`Comparable[String]?`) checks its inner type the same way (#2649).
        expected match
          case applied: AppliedClassType => boxSupertypeAccepts(table, applied, boxed)
          case n: NullableType if n.innerType.isInstanceOf[AppliedClassType] =>
            boxSupertypeAccepts(table, n.innerType.asInstanceOf[AppliedClassType], boxed)
          case _ => TypeRules.isAssignable(expected, boxed)
      }
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
