package onion.compiler.typing

import onion.compiler.TypedAST
import onion.compiler.TypedAST.{AppliedClassType, AsInstanceOf, ClassType, Method, NullableType, Nullability, Term, Type, TypeVariableType}

import scala.collection.immutable.Map

/** Well-known method names used in method resolution */
private[typing] object MethodNames {
  val LENGTH = "length"
  val SIZE = "size"
  val GET_PREFIX = "get"
  val IS_PREFIX = "is"
}

/** Type substitution helpers to reduce boilerplate in method typing */
private[typing] object TypeSubst {

  /**
   * Substitute type with only class-level type parameters from target type.
   * Unsubstituted variables stay as variables (rather than collapsing to
   * their bounds) so values read out of a generic body keep their static
   * type T and the nullable-deref check can see them.
   */
  def withClassOnly(typ: Type, targetType: Type): Type =
    TypeSubstitution.substituteType(
      typ,
      TypeSubstitution.classSubstitution(targetType),
      Map.empty,
      defaultToBound = false
    )

  /** Substitute type with both class and method type parameters */
  def apply(typ: Type, classSubst: Map[String, Type], methodSubst: Map[String, Type]): Type =
    TypeSubstitution.substituteType(typ, classSubst, methodSubst, defaultToBound = true)

  /**
   * Result-type substitution for calls: like apply, but unsubstituted class
   * type variables survive instead of collapsing to their bounds (see
   * withClassOnly). Method type variables are always bound by inference, so
   * this only affects T from the enclosing/receiver class.
   */
  def result(typ: Type, classSubst: Map[String, Type], methodSubst: Map[String, Type]): Type =
    TypeSubstitution.substituteType(typ, classSubst, methodSubst, defaultToBound = false)

  /** Substitute all argument types of a method */
  def args(method: Method, classSubst: Map[String, Type], methodSubst: Map[String, Type]): Array[Type] =
    method.arguments.map(tp => apply(tp, classSubst, methodSubst))(using onion.compiler.TypedAST.typeTag)

  /**
   * Like [[args]], for checking the arguments of a call already chosen: a
   * parameter declared as a bare (nullable) type variable that nothing binds
   * here -- `m(x: T)` called inside its own generic class -- collapses to the
   * nullable form of its bound (`Object?`), not `Object`, because a bare `[T]`
   * admits null (#2500). Overload selection keeps using [[args]].
   */
  def paramArgs(method: Method, classSubst: Map[String, Type], methodSubst: Map[String, Type]): Array[Type] =
    method.arguments.map { tp =>
      val substituted = apply(tp, classSubst, methodSubst)
      tp match {
        case tv: TypeVariableType
          if tv.nullability == Nullability.Nullable && !methodSubst.contains(tv.name) && !classSubst.contains(tv.name) =>
          NullableType.of(substituted)
        case _ => substituted
      }
    }(using onion.compiler.TypedAST.typeTag)

  /** Wrap term in AsInstanceOf if types differ, otherwise return as-is */
  def withCast(term: Term, targetType: Type): Term =
    if (targetType eq term.`type`) term else new AsInstanceOf(term, targetType)

  /** Option-returning version of withCast */
  def withCastOpt(term: Term, targetType: Type): Option[Term] =
    Some(withCast(term, targetType))

  /**
   * Recovers type arguments for a raw (unapplied) class from a scrutinee it
   * was matched out of: matching `Some` out of an `Opt[String]` scrutinee
   * specializes to `Some[String]` by unifying `Some`'s applied view of `Opt`
   * against the scrutinee's actual type arguments (#311). Anything not
   * solved that way -- no generics involved, an unparameterized scrutinee, a
   * variable left free -- keeps the raw type, so this only ever adds
   * precision.
   */
  def specializeFromScrutinee(matched: Type, scrutinee: Type, typing: onion.compiler.Typing): Type =
    specializeFromScrutinee(matched, selfApplied(scrutinee, typing))

  /**
   * `this` is typed by its raw class, so `select this` inside a generic class
   * (`enum Lookup[T]`'s methods) had no type arguments to recover: `f` in
   * `case f is Found` bound a raw `Found`, whose `value()` is `Found`'s own
   * `T`, not the enum's (#2308). Read the enclosing class, unapplied, as
   * applied to its own type parameters.
   */
  private def selfApplied(scrutinee: Type, typing: onion.compiler.Typing): Type = scrutinee match {
    case ct: ClassType
      if !ct.isInstanceOf[AppliedClassType] && ct.typeParameters.nonEmpty && (ct eq typing.currentDefinition) =>
      val vars = ct.typeParameters.toList.map(p => typing.typeParams_.get(p.name).map(_.variableType))
      if (vars.forall(_.isDefined)) AppliedClassType(ct, vars.map(_.get)) else scrutinee
    case _ => scrutinee
  }

  def specializeFromScrutinee(matched: Type, scrutinee: Type): Type = (matched, scrutinee) match {
    // The common case: the matched class *is* the scrutinee's own class (e.g.
    // destructuring `Wrap(v)` straight out of a `Wrap[Int]` scrutinee, no
    // sealed-hierarchy narrowing involved). The scrutinee's type arguments
    // already line up 1:1 with `raw`'s own type parameters, so this can skip
    // the ancestor-view unification below entirely -- which only ever
    // produces an empty-argument self view (AppliedTypeViews's traversal
    // starting point, not a real parameterization) and would fail to unify.
    case (raw: ClassType, applied: AppliedClassType)
      if !raw.isInstanceOf[AppliedClassType] && raw.typeParameters.nonEmpty &&
        TypeRelations.sameClass(raw, applied.raw) =>
      AppliedClassType(raw, applied.typeArguments.toList)
    case (raw: ClassType, applied: AppliedClassType)
      if !raw.isInstanceOf[AppliedClassType] && raw.typeParameters.nonEmpty =>
      val solved = scala.collection.mutable.HashMap[String, Type]()
      val paramNames = raw.typeParameters.map(_.name).toSet

      def unify(formal: Type, actual: Type): Unit = formal match {
        case tv: TypeVariableType if paramNames.contains(tv.name) =>
          if (!solved.contains(tv.name)) solved += tv.name -> actual
        case fa: AppliedClassType =>
          actual match {
            case aa: AppliedClassType
              if TypeRelations.sameClass(fa.raw, aa.raw) && fa.typeArguments.length == aa.typeArguments.length =>
              fa.typeArguments.zip(aa.typeArguments).foreach((f, a) => unify(f, a))
            case _ =>
          }
        case _ =>
      }

      AppliedTypeViews.collectAppliedViewsFrom(raw)
        .collectFirst { case (viewRaw, view) if TypeRelations.sameClass(viewRaw, applied.raw) => view }
        .foreach(view => unify(view, applied))

      if (raw.typeParameters.forall(tp => solved.contains(tp.name)))
        AppliedClassType(raw, raw.typeParameters.map(tp => solved(tp.name)).toList)
      else matched
    case _ => matched
  }
}
