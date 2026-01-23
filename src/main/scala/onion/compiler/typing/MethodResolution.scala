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
import scala.collection.mutable.HashMap

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
      case ct: ClassType =>
        val views = AppliedTypeViews.collectAppliedViewsFrom(ct)
        if views.isEmpty then target.findMethod(name, params)
        else findMethodsWithViews(ct, name, params, views, table)
      case _ =>
        target.findMethod(name, params)

  private def findMethodsWithViews(
    target: ObjectType,
    name: String,
    params: Array[Term],
    views: scala.collection.immutable.Map[ClassType, AppliedClassType],
    table: ClassTable
  ): Array[Method] =
    val candidates = new JTreeSet[Method](new MethodComparator)

    def collectMethods(tp: ObjectType): Unit =
      if tp == null then return
      tp.methods(name).foreach(candidates.add)
      collectMethods(tp.superClass)
      tp.interfaces.foreach(collectMethods)

    collectMethods(target)
    val specializedArgsCache = HashMap[Method, Array[Type]]()
    val methodArgsWithTypeParamsCache = HashMap[Method, Array[Type]]()
    val viewSubstCache = HashMap[ClassType, scala.collection.immutable.Map[String, Type]]()
    val emptySubst = scala.collection.immutable.Map.empty[String, Type]

    def ownerViewSubst(method: Method): scala.collection.immutable.Map[String, Type] =
      val owner0 = method.affiliation match
        case ap: AppliedClassType => ap.raw
        case ct: ClassType => ct
      viewSubstCache.getOrElseUpdate(
        owner0,
        views.get(owner0) match
          case Some(view) =>
            view.raw.typeParameters.map(_.name).zip(view.typeArguments).toMap
          case None =>
            emptySubst
      )

    def specializedArgs(method: Method): Array[Type] =
      specializedArgsCache.getOrElseUpdate(
        method,
        {
          val args = method.arguments
          val substituted = new Array[Type](args.length)
          val ownerSubst = ownerViewSubst(method)
          var i = 0
          while i < args.length do
            substituted(i) = TypeSubstitution.substituteType(args(i), ownerSubst, emptySubst, defaultToBound = true)
            i += 1
          substituted
        }
      )

    // メソッドの型パラメータを保持したまま引数型を取得（クラスの型パラメータのみ置換）
    def methodArgsWithTypeParams(method: Method): Array[Type] =
      methodArgsWithTypeParamsCache.getOrElseUpdate(
        method,
        {
          val args = method.arguments
          val substituted = new Array[Type](args.length)
          val ownerSubst = ownerViewSubst(method)
          var i = 0
          while i < args.length do
            // defaultToBound = false でメソッドの型パラメータを保持
            substituted(i) = TypeSubstitution.substituteType(args(i), ownerSubst, emptySubst, defaultToBound = false)
            i += 1
          substituted
        }
      )

    def isAssignableWithBoxing(target: Type, source: Type): Boolean =
      // 通常の型チェック
      if TypeRules.isSuperType(target, source) then return true

      // プリミティブ型 → 参照型: ボクシング
      if !target.isBasicType && source.isBasicType then
        val basicType = source.asInstanceOf[BasicType]
        if basicType == BasicType.VOID then return false
        val boxedType = toolbox.Boxing.boxedType(table, basicType)
        return TypeRules.isSuperType(target, boxedType)

      // 参照型 → プリミティブ型: アンボクシング
      if target.isBasicType && !source.isBasicType then
        val targetBasicType = target.asInstanceOf[BasicType]
        if targetBasicType == BasicType.VOID then return false
        val boxedType = toolbox.Boxing.boxedType(table, targetBasicType)
        return TypeRules.isSuperType(boxedType, source)

      // AppliedClassType同士の比較: 型引数のボクシングを考慮
      (target, source) match
        case (targetApplied: AppliedClassType, sourceApplied: AppliedClassType) =>
          sameClass(targetApplied.raw, sourceApplied.raw) &&
            targetApplied.typeArguments.length == sourceApplied.typeArguments.length &&
            targetApplied.typeArguments.zip(sourceApplied.typeArguments).forall { (t, s) =>
              isAssignableWithBoxing(t, s)
            }
        case _ =>
          false

    // ジェネリックメソッドの型パラメータを含む型かどうかを判定
    def containsMethodTypeParam(tp: Type, methodTypeParams: Set[String]): Boolean = tp match
      case tv: TypeVariableType => methodTypeParams.contains(tv.name)
      case applied: AppliedClassType => applied.typeArguments.exists(containsMethodTypeParam(_, methodTypeParams))
      case at: ArrayType => containsMethodTypeParam(at.base, methodTypeParams)
      case _ => false

    // 同じクラス名かどうかをチェック（参照比較と名前比較の両方）
    def sameClass(c1: ClassType, c2: ClassType): Boolean =
      (c1 eq c2) || (c1.name == c2.name)

    // 型引数比較用：ボクシングを考慮した構造マッチング
    def structuralMatchWithBoxing(expected: Type, actual: Type, methodTypeParams: Set[String]): Boolean =
      // プリミティブ型 vs ラッパー型の比較
      if expected.isBasicType && !actual.isBasicType then
        val boxedExpected = toolbox.Boxing.boxedType(table, expected.asInstanceOf[BasicType])
        sameClass(boxedExpected, actual.asInstanceOf[ClassType])
      else if !expected.isBasicType && actual.isBasicType then
        val boxedActual = toolbox.Boxing.boxedType(table, actual.asInstanceOf[BasicType])
        structuralMatch(expected, boxedActual, methodTypeParams)
      else
        structuralMatch(expected, actual, methodTypeParams)

    // 構造的に互換性があるかチェック（型パラメータは寛容に、ボクシングも考慮）
    def structuralMatch(expected: Type, actual: Type, methodTypeParams: Set[String]): Boolean =
      (expected, actual) match
        case (tv: TypeVariableType, _) if methodTypeParams.contains(tv.name) =>
          // メソッドの型パラメータは任意の型にマッチ（上限境界を満たすかチェック）
          // プリミティブ型の場合はボクシングして比較
          if actual.isBasicType then
            val boxedActual = toolbox.Boxing.boxedType(table, actual.asInstanceOf[BasicType])
            TypeRules.isSuperType(tv.upperBound, boxedActual)
          else
            TypeRules.isSuperType(tv.upperBound, actual)
        case (ae: AppliedClassType, aa: AppliedClassType) =>
          // 同じrawクラスで、型引数が構造的にマッチ（名前比較も使用）
          sameClass(ae.raw, aa.raw) &&
            ae.typeArguments.length == aa.typeArguments.length &&
            ae.typeArguments.zip(aa.typeArguments).forall { (e, a) => structuralMatchWithBoxing(e, a, methodTypeParams) }
        case (ae: AppliedClassType, aa: ClassType) if !aa.isInstanceOf[AppliedClassType] =>
          // AppliedClassType vs raw ClassType - rawクラスが同じで、expected側の型引数が全て型パラメータなら許可
          sameClass(ae.raw, aa) && ae.typeArguments.forall {
            case tv: TypeVariableType => methodTypeParams.contains(tv.name)
            case _ => false
          }
        case _ =>
          isAssignableWithBoxing(expected, actual)

    // 型パラメータを考慮した互換性チェック
    def isCompatibleWithTypeParams(expected: Type, actual: Type, methodTypeParams: Set[String]): Boolean =
      if methodTypeParams.nonEmpty && containsMethodTypeParam(expected, methodTypeParams) then
        structuralMatch(expected, actual, methodTypeParams)
      else
        isAssignableWithBoxing(expected, actual)

    def applicable(method: Method): Boolean =
      val expected = specializedArgs(method)
      // メソッド自身の型パラメータを収集（クラスの型パラメータは除く）
      val methodTypeParams = method.typeParameters.map(_.name).toSet
      // 型パラメータを保持した引数型を取得（構造マッチング用）
      val argsWithTypeParams = if methodTypeParams.nonEmpty then methodArgsWithTypeParams(method) else expected

      // 型パラメータを考慮した引数チェック
      def checkArg(idx: Int): Boolean =
        if methodTypeParams.nonEmpty && containsMethodTypeParam(argsWithTypeParams(idx), methodTypeParams) then
          structuralMatch(argsWithTypeParams(idx), params(idx).`type`, methodTypeParams)
        else
          isAssignableWithBoxing(expected(idx), params(idx).`type`)

      if (method.isVararg && expected.nonEmpty) {
        // For vararg methods
        val fixedArgCount = expected.length - 1
        val varargType = expected.last
        if (!varargType.isArrayType) return false
        val componentType = varargType.asInstanceOf[ArrayType].base
        val componentTypeWithParams = if methodTypeParams.nonEmpty then
          argsWithTypeParams.last match
            case at: ArrayType => at.base
            case _ => componentType
        else componentType

        // Must have at least the fixed arguments
        if (params.length < fixedArgCount) return false

        // Check fixed args
        var i = 0
        while i < fixedArgCount do
          if !checkArg(i) then return false
          i += 1

        // Check vararg portion
        if (params.length == expected.length) {
          // Could be direct array pass or single element
          val lastParamType = params.last.`type`
          if (lastParamType.isArrayType && TypeRules.isSuperType(varargType, lastParamType)) {
            return true
          }
          // Single element case - 型パラメータを考慮
          if methodTypeParams.nonEmpty && containsMethodTypeParam(componentTypeWithParams, methodTypeParams) then
            structuralMatch(componentTypeWithParams, lastParamType, methodTypeParams)
          else
            isAssignableWithBoxing(componentType, lastParamType)
        } else if (params.length > expected.length) {
          // Multiple vararg elements
          var j = fixedArgCount
          while j < params.length do
            val argOk = if methodTypeParams.nonEmpty && containsMethodTypeParam(componentTypeWithParams, methodTypeParams) then
              structuralMatch(componentTypeWithParams, params(j).`type`, methodTypeParams)
            else
              isAssignableWithBoxing(componentType, params(j).`type`)
            if !argOk then return false
            j += 1
          true
        } else {
          // No vararg elements (empty array)
          true
        }
      } else {
        // Non-vararg method: original logic with default args
        if params.length < method.minArguments || params.length > expected.length then return false
        var i = 0
        while i < params.length do
          if !checkArg(i) then return false
          i += 1
        true
      }

    val applicableMethods = candidates.asScala.filter(applicable).toList
    if applicableMethods.isEmpty then return new Array[Method](0)
    if applicableMethods.length == 1 then return Array(applicableMethods.head)

    val sorter: java.util.Comparator[Method] = new java.util.Comparator[Method] {
      def compare(m1: Method, m2: Method): Int =
        val a1 = specializedArgs(m1)
        val a2 = specializedArgs(m2)
        if TypeRules.isAllSuperType(a2, a1) then -1
        else if TypeRules.isAllSuperType(a1, a2) then 1
        else 0
    }

    val selected = new java.util.ArrayList[Method]()
    selected.addAll(applicableMethods.asJava)
    java.util.Collections.sort(selected, sorter)
    if selected.size < 2 then
      selected.toArray(new Array[Method](0))
    else
      val m1 = selected.get(0)
      val m2 = selected.get(1)
      if sorter.compare(m1, m2) >= 0 then
        selected.toArray(new Array[Method](0))
      else
        Array[Method](m1)
}
