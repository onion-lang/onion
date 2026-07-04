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
        isAssignableWithBoxing(expected(idx), params(idx).`type`) ||
          TypeRules.emptyCollectionLiteralAccepts(expected(idx), params(idx))

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
        else primitiveExactTiebreak(a1, a2)
      }
    }

  /**
   * Java-style most-specific tiebreak for primitive vs. reference overloads.
   *
   * When the subtype rule leaves two overloads incomparable (e.g.
   * `remove(int)` vs `remove(Object)` called with an Onion `Int`), prefer the
   * candidate whose formal parameter is the *exact* primitive matching the
   * primitive argument over one taking a reference/boxed/`Object` formal. This
   * mirrors JLS overload resolution narrowly: it only fires at positions where
   * the actual argument is a primitive and exactly one formal is that same
   * primitive while the other is a non-basic (reference) type. If both sides
   * "win" some positions (or neither does), the result stays ambiguous (0).
   */
  private def primitiveExactTiebreak(a1: Array[Type], a2: Array[Type]): Int = {
    if a1.length != a2.length || a1.length != params.length then return 0
    var oneBetter = false
    var twoBetter = false
    var i = 0
    while i < params.length do
      val actual = params(i).`type`
      if actual != null && actual.isBasicType then
        val f1 = a1(i)
        val f2 = a2(i)
        val f1Exact = (f1 eq actual)
        val f2Exact = (f2 eq actual)
        // "loses" = the other formal is a reference type (not the same primitive)
        if f1Exact && !f2Exact && !f2.isBasicType then oneBetter = true
        else if f2Exact && !f1Exact && !f1.isBasicType then twoBetter = true
      i += 1
    if oneBetter && !twoBetter then -1
    else if twoBetter && !oneBetter then 1
    else 0
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
    case n: NullableType => containsMethodTypeParam(n.innerType, methodTypeParams)
    // A method type variable can hide inside a wildcard bound, e.g. the JDK's
    // `invokeAll(Collection[? extends Callable[T]])`. Without descending into
    // the wildcard bounds, T is treated as non-generic and the formal defaults
    // to its erased `Object` bound, so the call fails to resolve (issue #274).
    case w: WildcardType =>
      containsMethodTypeParam(w.upperBound, methodTypeParams) ||
        w.lowerBound.exists(containsMethodTypeParam(_, methodTypeParams))
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
      // A wildcard formal that carries a method type variable (e.g.
      // `? extends Callable[T]`) matches by descending into its bound. The
      // covariant `? extends E` case requires the actual argument to structurally
      // match E (so T can bind); `? super E` matches E contravariantly. Bounds
      // with no method type variable fall through to plain assignability so the
      // existing wildcard rules in isAssignable keep applying (issue #274).
      case (w: WildcardType, _) if containsMethodTypeParam(w, methodTypeParams) =>
        w.lowerBound match
          case Some(lb) => structuralMatch(lb, actual, methodTypeParams)
          case None => structuralMatch(w.upperBound, actual, methodTypeParams)
      case (ae: AppliedClassType, aa: AppliedClassType) =>
        if sameClass(ae.raw, aa.raw) && ae.typeArguments.length == aa.typeArguments.length then
          ae.typeArguments.zip(aa.typeArguments).forall { (e, a) =>
            structuralMatchWithBoxing(e, a, methodTypeParams)
          }
        else
          // Raw classes differ (e.g. formal Collection[...] vs actual
          // ArrayList[...]): align via the actual's applied supertype views so a
          // method type variable buried in a wildcard bound still gets a chance
          // to match structurally (issue #274).
          val actualViews = AppliedTypeViews.collectAppliedViewsFrom(aa)
          actualViews.get(ae.raw)
            .orElse(actualViews.find((k, _) => k.name == ae.raw.name).map(_._2)) match
            case Some(view) =>
              ae.typeArguments.length == view.typeArguments.length &&
                ae.typeArguments.zip(view.typeArguments).forall { (e, a) =>
                  structuralMatchWithBoxing(e, a, methodTypeParams)
                }
            case None =>
              isAssignableWithBoxing(expected, actual)
      case (ae: AppliedClassType, aa: ClassType) if !aa.isInstanceOf[AppliedClassType] =>
        sameClass(ae.raw, aa) && ae.typeArguments.forall {
          case tv: TypeVariableType => methodTypeParams.contains(tv.name)
          case _ => false
        }
      case _ =>
        isAssignableWithBoxing(expected, actual)
}
