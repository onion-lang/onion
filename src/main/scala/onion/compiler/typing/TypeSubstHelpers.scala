package onion.compiler.typing

import onion.compiler.TypedAST
import onion.compiler.TypedAST.{AsInstanceOf, Method, Term, Type}

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
    method.arguments.map(tp => apply(tp, classSubst, methodSubst))

  /** Wrap term in AsInstanceOf if types differ, otherwise return as-is */
  def withCast(term: Term, targetType: Type): Term =
    if (targetType eq term.`type`) term else new AsInstanceOf(term, targetType)

  /** Option-returning version of withCast */
  def withCastOpt(term: Term, targetType: Type): Option[Term] =
    Some(withCast(term, targetType))
}
