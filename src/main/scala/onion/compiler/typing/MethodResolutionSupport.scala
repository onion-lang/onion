package onion.compiler.typing

import onion.compiler.TypedAST.*
import onion.compiler.ClassTable

import scala.collection.mutable.HashMap

private[compiler] final class MethodResolutionSupport(
  views: scala.collection.immutable.Map[ClassType, AppliedClassType],
  params: Array[Term],
  table: ClassTable
) {
  private val specializedArgsCache = HashMap[Method, Array[Type]]()
  private val methodArgsWithTypeParamsCache = HashMap[Method, Array[Type]]()
  private val viewSubstCache = HashMap[ClassType, scala.collection.immutable.Map[String, Type]]()
  private val emptySubst = scala.collection.immutable.Map.empty[String, Type]

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

  def applicable(method: Method): Boolean = {
    val expected = specializedArgs(method)
    val methodTypeParams = method.typeParameters.map(_.name).toSet
    val argsWithTypeParams =
      if methodTypeParams.nonEmpty then methodArgsWithTypeParams(method) else expected

    def checkArg(idx: Int): Boolean =
      if methodTypeParams.nonEmpty && containsMethodTypeParam(argsWithTypeParams(idx), methodTypeParams) then
        structuralMatch(argsWithTypeParams(idx), params(idx).`type`, methodTypeParams)
      else
        isAssignableWithBoxing(expected(idx), params(idx).`type`)

    if method.isVararg && expected.nonEmpty then
      val fixedArgCount = expected.length - 1
      val varargType = expected.last
      if !varargType.isArrayType then return false

      val componentType = varargType.asInstanceOf[ArrayType].base
      val componentTypeWithParams =
        if methodTypeParams.nonEmpty then
          argsWithTypeParams.last match
            case at: ArrayType => at.base
            case _ => componentType
        else componentType

      if params.length < fixedArgCount then return false

      var i = 0
      while i < fixedArgCount do
        if !checkArg(i) then return false
        i += 1

      if params.length == expected.length then
        val lastParamType = params.last.`type`
        if lastParamType.isArrayType && TypeRules.isSuperType(varargType, lastParamType) then
          true
        else if methodTypeParams.nonEmpty && containsMethodTypeParam(componentTypeWithParams, methodTypeParams) then
          structuralMatch(componentTypeWithParams, lastParamType, methodTypeParams)
        else
          isAssignableWithBoxing(componentType, lastParamType)
      else if params.length > expected.length then
        var j = fixedArgCount
        while j < params.length do
          val argOk =
            if methodTypeParams.nonEmpty && containsMethodTypeParam(componentTypeWithParams, methodTypeParams) then
              structuralMatch(componentTypeWithParams, params(j).`type`, methodTypeParams)
            else
              isAssignableWithBoxing(componentType, params(j).`type`)
          if !argOk then return false
          j += 1
        true
      else
        true
    else
      if params.length < method.minArguments || params.length > expected.length then return false
      var i = 0
      while i < params.length do
        if !checkArg(i) then return false
        i += 1
      true
  }

  val specificityComparator: java.util.Comparator[Method] =
    new java.util.Comparator[Method] {
      override def compare(m1: Method, m2: Method): Int = {
        val a1 = specializedArgs(m1)
        val a2 = specializedArgs(m2)
        if TypeRules.isAllSuperType(a2, a1) then -1
        else if TypeRules.isAllSuperType(a1, a2) then 1
        else 0
      }
    }

  private def ownerViewSubst(method: Method): scala.collection.immutable.Map[String, Type] = {
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
  }

  private def methodArgsWithTypeParams(method: Method): Array[Type] =
    methodArgsWithTypeParamsCache.getOrElseUpdate(
      method,
      {
        val args = method.arguments
        val substituted = new Array[Type](args.length)
        val ownerSubst = ownerViewSubst(method)
        var i = 0
        while i < args.length do
          substituted(i) = TypeSubstitution.substituteType(args(i), ownerSubst, emptySubst, defaultToBound = false)
          i += 1
        substituted
      }
    )

  private def isAssignableWithBoxing(target: Type, source: Type): Boolean =
    TypeRelations.isAssignableWithBoxing(target, source, table)

  private def containsMethodTypeParam(tp: Type, methodTypeParams: Set[String]): Boolean = tp match
    case tv: TypeVariableType => methodTypeParams.contains(tv.name)
    case applied: AppliedClassType => applied.typeArguments.exists(containsMethodTypeParam(_, methodTypeParams))
    case at: ArrayType => containsMethodTypeParam(at.base, methodTypeParams)
    case _ => false

  private def sameClass(c1: ClassType, c2: ClassType): Boolean =
    TypeRelations.sameClass(c1, c2)

  private def structuralMatchWithBoxing(expected: Type, actual: Type, methodTypeParams: Set[String]): Boolean =
    if expected.isBasicType && !actual.isBasicType then
      val boxedExpected = onion.compiler.toolbox.Boxing.boxedType(table, expected.asInstanceOf[BasicType])
      sameClass(boxedExpected, actual.asInstanceOf[ClassType])
    else if !expected.isBasicType && actual.isBasicType then
      val boxedActual = onion.compiler.toolbox.Boxing.boxedType(table, actual.asInstanceOf[BasicType])
      structuralMatch(expected, boxedActual, methodTypeParams)
    else
      structuralMatch(expected, actual, methodTypeParams)

  private def structuralMatch(expected: Type, actual: Type, methodTypeParams: Set[String]): Boolean =
    (expected, actual) match
      case (tv: TypeVariableType, _) if methodTypeParams.contains(tv.name) =>
        if actual.isBasicType then
          val boxedActual = onion.compiler.toolbox.Boxing.boxedType(table, actual.asInstanceOf[BasicType])
          TypeRules.isSuperType(tv.upperBound, boxedActual)
        else
          TypeRules.isSuperType(tv.upperBound, actual)
      case (ae: AppliedClassType, aa: AppliedClassType) =>
        sameClass(ae.raw, aa.raw) &&
          ae.typeArguments.length == aa.typeArguments.length &&
          ae.typeArguments.zip(aa.typeArguments).forall { (e, a) =>
            structuralMatchWithBoxing(e, a, methodTypeParams)
          }
      case (ae: AppliedClassType, aa: ClassType) if !aa.isInstanceOf[AppliedClassType] =>
        sameClass(ae.raw, aa) && ae.typeArguments.forall {
          case tv: TypeVariableType => methodTypeParams.contains(tv.name)
          case _ => false
        }
      case _ =>
        isAssignableWithBoxing(expected, actual)
}
