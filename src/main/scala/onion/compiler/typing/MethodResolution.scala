/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.typing

import onion.compiler.TypedAST.*
import onion.compiler.{ClassTable, toolbox}

import java.util.{TreeSet => JTreeSet}

import scala.jdk.CollectionConverters.*

/**
 * Method Resolution with Overloading and Generics
 *
 * This object provides the core method resolution logic for the Onion compiler,
 * handling complex scenarios including:
 *   - Method overloading resolution
 *   - Generic method type parameter inference
 *   - Automatic boxing/unboxing
 *   - Vararg method handling
 *   - Default parameter support
 *
 * == Resolution Algorithm ==
 *
 * Method resolution follows these steps:
 *
 * '''1. Candidate Collection''': Gather all methods with the matching name
 * from the target type and its supertypes.
 *
 * '''2. Applicability Check''': Filter to methods that can accept the given
 * arguments considering:
 *   - Argument count (with default parameters and varargs)
 *   - Type compatibility (with boxing and generics)
 *
 * '''3. Most Specific Selection''': If multiple candidates remain, select
 * the most specific method using subtype relationships.
 *
 * == Type Substitution ==
 *
 * For generic types like `List[String]`, method resolution substitutes
 * type parameters with their actual type arguments before checking
 * argument compatibility.
 *
 * Example:
 * {{{
 * val list: List[String] = ...
 * list.add("hello")  // add(elem: T) becomes add(elem: String)
 * }}}
 *
 * == Boxing Considerations ==
 *
 * The resolution considers automatic boxing/unboxing:
 * {{{
 * def foo(x: Integer): Unit = ...
 * foo(42)  // int auto-boxed to Integer
 * }}}
 *
 * @see [[TypeSubstitution]] for type variable substitution
 * @see [[TypeRules]] for type compatibility rules
 * @see [[AppliedTypeViews]] for generic type handling
 */
private[compiler] object MethodResolution {

  /**
   * Finds applicable methods for a method call.
   *
   * This is the main entry point for method resolution. It handles:
   *   - Collection of candidate methods from the target and its supertypes
   *   - Type substitution for generic types
   *   - Applicability checking with boxing and varargs
   *   - Selection of the most specific method
   *
   * @param target The type on which the method is being called
   * @param name The method name
   * @param params The actual argument terms (with their types)
   * @param table The class table for type lookup and boxing
   * @return Array of applicable methods (0 if none found, 1 if unique, 2+ if ambiguous)
   */
  def findMethods(target: ObjectType, name: String, params: Array[Term], table: ClassTable): Array[Method] =
    target match
      case ct: AppliedClassType =>
        val views = AppliedTypeViews.collectAppliedViewsFrom(ct)
        findMethodsWithViews(ct, name, params, views, table)
      case ct: ClassType if requiresSpecializedViews(ct) =>
        val views = AppliedTypeViews.collectAppliedViewsFrom(ct)
        findMethodsWithViews(ct, name, params, views, table)
      case ct: ClassType =>
        findMethodsWithViews(ct, name, params, scala.collection.immutable.Map.empty, table)
      case _ =>
        target.findMethod(name, params)

  private def requiresSpecializedViews(target: ClassType): Boolean =
    val visited = scala.collection.mutable.HashSet[String]()

    def loop(tp: ClassType): Boolean =
      if tp == null then false
      else tp match
        case _: AppliedClassType =>
          true
        case raw if !visited.add(raw.name) =>
          false
        case raw =>
          loop(raw.superClass) || raw.interfaces.exists(loop)

    loop(target.superClass) || target.interfaces.exists(loop)

  private def findMethodsWithViews(
    target: ObjectType,
    name: String,
    params: Array[Term],
    views: scala.collection.immutable.Map[ClassType, AppliedClassType],
    table: ClassTable
  ): Array[Method] =
    val candidates = new JTreeSet[Method](new MethodComparator)
    val support = new MethodResolutionSupport(views, params, table)

    def collectMethods(tp: ObjectType): Unit =
      if tp == null then return
      tp.methods(name).foreach(candidates.add)
      collectMethods(tp.superClass)
      tp.interfaces.foreach(collectMethods)

    collectMethods(target)
    val applicableMethods = candidates.asScala.filter(support.applicable).toList
    if applicableMethods.isEmpty then return new Array[Method](0)
    if applicableMethods.length == 1 then return Array(applicableMethods.head)

    val selected = new java.util.ArrayList[Method]()
    selected.addAll(applicableMethods.asJava)
    java.util.Collections.sort(selected, support.specificityComparator)
    if selected.size < 2 then
      selected.toArray(new Array[Method](0))
    else
      val m1 = selected.get(0)
      val m2 = selected.get(1)
      if support.specificityComparator.compare(m1, m2) >= 0 then
        selected.toArray(new Array[Method](0))
      else
        Array[Method](m1)
}
