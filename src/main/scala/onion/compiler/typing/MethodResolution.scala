package onion.compiler.typing

import onion.compiler.TypedAST.*
import onion.compiler.{ClassTable, toolbox}

import java.util.{TreeSet => JTreeSet}

import scala.jdk.CollectionConverters.*
import scala.collection.mutable.HashMap

private[compiler] object MethodResolution {
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

    def ownerViewSubst(method: Method): scala.collection.immutable.Map[String, Type] =
      val owner0 = method.affiliation match
        case ap: AppliedClassType => ap.raw
        case ct: ClassType => ct
      views.get(owner0) match
        case Some(view) =>
          view.raw.typeParameters.map(_.name).zip(view.typeArguments).toMap
        case None =>
          scala.collection.immutable.Map.empty

    def specializedArgs(method: Method): Array[Type] =
      specializedArgsCache.getOrElseUpdate(
        method,
        method.arguments.map(tp => TypeSubstitution.substituteType(tp, ownerViewSubst(method), scala.collection.immutable.Map.empty, defaultToBound = true))
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

      false

    def applicable(method: Method): Boolean =
      val expected = specializedArgs(method)
      if expected.length != params.length then return false
      var i = 0
      while i < expected.length do
        if !isAssignableWithBoxing(expected(i), params(i).`type`) then return false
        i += 1
      true

    val applicableMethods = candidates.asScala.filter(applicable).toList
    if applicableMethods.isEmpty then return new Array[Method](0)
    if applicableMethods.length == 1 then return Array(applicableMethods.head)

    def isAllSuperType(a: Array[Type], b: Array[Type]): Boolean =
      var i = 0
      while i < a.length do
        if !TypeRules.isSuperType(a(i), b(i)) then return false
        i += 1
      true

    val sorter: java.util.Comparator[Method] = new java.util.Comparator[Method] {
      def compare(m1: Method, m2: Method): Int =
        val a1 = specializedArgs(m1)
        val a2 = specializedArgs(m2)
        if isAllSuperType(a2, a1) then -1
        else if isAllSuperType(a1, a2) then 1
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

