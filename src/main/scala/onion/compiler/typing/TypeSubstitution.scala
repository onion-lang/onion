/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.typing

import onion.compiler.TypedAST
import onion.compiler.TypedAST.{ArrayType, Type}

import scala.collection.mutable.HashMap

/**
 * Type Substitution Utilities for Generic Type Handling
 *
 * This object provides utilities for substituting type variables with
 * concrete types in the context of generic classes and methods.
 *
 * == Type Substitution Overview ==
 *
 * When a generic type is applied with type arguments, type variables in the
 * generic definition need to be replaced with the actual type arguments.
 *
 * Example:
 * {{{
 * // Generic class definition
 * class List[T] {
 *   def add(elem: T): Unit = ...
 * }
 *
 * // Usage with type argument
 * val strings: List[String] = new List[String]()
 * strings.add("hello")  // T is substituted with String
 * }}}
 *
 * == Two Levels of Substitution ==
 *
 * '''Class-level substitution''': Type parameters from the enclosing class.
 * Built from `AppliedClassType` nodes (e.g., `List[String]`).
 *
 * '''Method-level substitution''': Type parameters from a generic method.
 * Built from method type argument inference or explicit specification.
 *
 * Method substitution takes precedence over class substitution when the
 * same type variable name is used in both scopes.
 *
 * @see [[onion.compiler.TypedAST.TypeVariableType]] for type variable representation
 * @see [[onion.compiler.TypedAST.AppliedClassType]] for applied generic types
 */
private[compiler] object TypeSubstitution {

  /**
   * Builds a type substitution map from an applied class type.
   *
   * Given `List[String]`, this returns `Map("T" -> String)`.
   *
   * @param tp The type to extract substitution from
   * @return A map from type parameter names to their actual type arguments
   */
  def classSubstitution(tp: Type): scala.collection.immutable.Map[String, Type] = tp match {
    case applied: TypedAST.AppliedClassType =>
      val rawParams = applied.raw.typeParameters
      rawParams.map(_.name).zip(applied.typeArguments).toMap
    case _ =>
      scala.collection.immutable.Map.empty
  }

  /**
   * Applies type substitution to a type, replacing type variables with their mapped types.
   *
   * This method recursively traverses the type structure, substituting type variables
   * where substitutions are available. It handles:
   *   - Type variable substitution
   *   - Applied class types (recursive substitution in type arguments)
   *   - Array types (substitution in component type)
   *   - Wildcard types (substitution in bounds)
   *
   * @param tp The type to perform substitution on
   * @param classSubst Substitution map from enclosing class type parameters
   * @param methodSubst Substitution map from method type parameters (takes precedence)
   * @param defaultToBound If true, unsubstituted type variables are replaced with their upper bounds;
   *                       if false, type variables remain as-is
   * @return The type with all applicable substitutions applied
   */
  def substituteType(
    tp: Type,
    classSubst: scala.collection.immutable.Map[String, Type],
    methodSubst: scala.collection.immutable.Map[String, Type],
    defaultToBound: Boolean
  ): Type = {
    def lookup(name: String): Option[Type] = methodSubst.get(name).orElse(classSubst.get(name))
    tp match {
      case tv: TypedAST.TypeVariableType =>
        lookup(tv.name).getOrElse(if (defaultToBound) tv.upperBound else tv)
      case applied: TypedAST.AppliedClassType =>
        val newArgs = applied.typeArguments.map(arg => substituteType(arg, classSubst, methodSubst, defaultToBound))
        if (newArgs.sameElements(applied.typeArguments)) applied
        else TypedAST.AppliedClassType(applied.raw, newArgs.toList)
      case at: ArrayType =>
        val newComponent = substituteType(at.component, classSubst, methodSubst, defaultToBound)
        if (newComponent eq at.component) at
        else at.table.loadArray(newComponent, at.dimension)
      case w: TypedAST.WildcardType =>
        val newUpper = substituteType(w.upperBound, classSubst, methodSubst, defaultToBound)
        val newLower = w.lowerBound.map(lb => substituteType(lb, classSubst, methodSubst, defaultToBound))
        if ((newUpper eq w.upperBound) && newLower == w.lowerBound) w
        else new TypedAST.WildcardType(newUpper, newLower)
      case other =>
        other
    }
  }
}
