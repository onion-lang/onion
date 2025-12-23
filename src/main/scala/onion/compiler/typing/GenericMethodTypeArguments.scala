package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

import scala.collection.mutable.HashMap

private[typing] object GenericMethodTypeArguments {
  def explicitFromMappedArgs(
    typing: Typing,
    callNode: AST.Node,
    method: Method,
    mappedArgs: Array[Type],
    classSubst: scala.collection.immutable.Map[String, Type],
    reportErrors: Boolean = true,
    typeArgNodes: Array[AST.TypeNode] = null
  ): Option[scala.collection.immutable.Map[String, Type]] = {
    import typing.*
    val typeParams = method.typeParameters
    if (typeParams.isEmpty) {
      if (reportErrors) {
        report(METHOD_NOT_GENERIC, callNode, method.affiliation.name, method.name)
      }
      return None
    }
    if (typeParams.length != mappedArgs.length) {
      if (reportErrors) {
        report(
          METHOD_TYPE_ARGUMENT_ARITY_MISMATCH,
          callNode,
          method.affiliation.name,
          method.name,
          Integer.valueOf(typeParams.length),
          Integer.valueOf(mappedArgs.length)
        )
      }
      return None
    }

    var subst: scala.collection.immutable.Map[String, Type] = scala.collection.immutable.Map.empty
    var i = 0
    while (i < typeParams.length) {
      subst = subst.updated(typeParams(i).name, mappedArgs(i))
      i += 1
    }

    i = 0
    while (i < typeParams.length) {
      val upper0 = typeParams(i).upperBound.getOrElse(rootClass)
      val upper = TypeSubstitution.substituteType(upper0, classSubst, subst, defaultToBound = true)
      val arg = mappedArgs(i)
      if (!TypeRules.isAssignable(upper, boxedTypeArgument(arg))) {
        if (reportErrors) {
          val position = if (typeArgNodes == null || i >= typeArgNodes.length) callNode else typeArgNodes(i)
          report(INCOMPATIBLE_TYPE, position, upper, arg)
        }
        return None
      }
      i += 1
    }

    Some(subst)
  }

  def infer(
    typing: Typing,
    callNode: AST.Node,
    method: Method,
    args: Array[Term],
    classSubst: scala.collection.immutable.Map[String, Type]
  ): scala.collection.immutable.Map[String, Type] =
    infer(typing, callNode, method, args, classSubst, null)

  def explicit(
    typing: Typing,
    callNode: AST.Node,
    method: Method,
    typeArgs: List[AST.TypeNode],
    classSubst: scala.collection.immutable.Map[String, Type]
  ): Option[scala.collection.immutable.Map[String, Type]] = {
    import typing.*
    val mappedArgs = new Array[Type](typeArgs.length)
    var i = 0
    while (i < typeArgs.length) {
      val mapped = mapFrom(typeArgs(i))
      if (mapped == null) return None
      if (mapped eq BasicType.VOID) {
        report(TYPE_ARGUMENT_MUST_BE_REFERENCE, typeArgs(i), mapped.name)
        return None
      }
      mappedArgs(i) = mapped
      i += 1
    }
    explicitFromMappedArgs(typing, callNode, method, mappedArgs, classSubst, typeArgNodes = typeArgs.toArray)
  }

  def infer(
    typing: Typing,
    callNode: AST.Node,
    method: Method,
    args: Array[Term],
    classSubst: scala.collection.immutable.Map[String, Type],
    expectedReturn: Type
  ): scala.collection.immutable.Map[String, Type] = {
    import typing.*
    val typeParams = method.typeParameters
    if (typeParams.isEmpty) return scala.collection.immutable.Map.empty

    def isSuperTypeForBounds(left: Type, right: Type): Boolean =
      if (!left.isBasicType && right.isBasicType) TypeRules.isSuperType(left, boxedTypeArgument(right))
      else TypeRules.isSuperType(left, right)

    def isAssignableForBounds(left: Type, right: Type): Boolean =
      if (!left.isBasicType && right.isBasicType) TypeRules.isAssignable(left, boxedTypeArgument(right))
      else TypeRules.isAssignable(left, right)

    val bounds = HashMap[String, Type]()
    for (tp <- typeParams) {
      val upper = tp.upperBound.getOrElse(rootClass)
      bounds += tp.name -> TypeSubstitution.substituteType(upper, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true)
    }

    val inferred = HashMap[String, Type]()
    val upperConstraints = HashMap[String, Type]()
    val lowerConstraints = HashMap[String, Type]()
    val paramNames = typeParams.map(_.name).toSet

    def addUpper(name: String, bound: Type, position: AST.Node): Unit = {
      if (bound == null || bound.isNullType) return
      upperConstraints.get(name) match
        case None =>
          upperConstraints += name -> bound
        case Some(prev) =>
          if (isSuperTypeForBounds(prev, bound)) upperConstraints += name -> bound
          else if (isSuperTypeForBounds(bound, prev)) ()
          else report(INCOMPATIBLE_TYPE, position, prev, bound)
    }

    def addLower(name: String, bound: Type): Unit = {
      if (bound == null || bound.isNullType) return
      lowerConstraints.get(name) match
        case None =>
          lowerConstraints += name -> bound
        case Some(prev) =>
          if (isSuperTypeForBounds(prev, bound)) ()
          else if (isSuperTypeForBounds(bound, prev)) lowerConstraints += name -> bound
          else lowerConstraints += name -> rootClass
    }

    def unify(formal: Type, actual: Type, position: AST.Node): Unit = {
      if (actual.isNullType) return
      formal match
        case w: TypedAST.WildcardType =>
          w.lowerBound match
            case Some(lb) =>
              lb match
                case tv: TypedAST.TypeVariableType if paramNames.contains(tv.name) =>
                  addUpper(tv.name, actual, position)
                case _ =>
                  unify(lb, actual, position)
            case None =>
              w.upperBound match
                case tv: TypedAST.TypeVariableType if paramNames.contains(tv.name) =>
                  addLower(tv.name, actual)
                case _ =>
                  unify(w.upperBound, actual, position)
        case tv: TypedAST.TypeVariableType if paramNames.contains(tv.name) =>
          inferred.get(tv.name) match {
            case Some(prev) =>
              if (!(prev eq actual)) report(INCOMPATIBLE_TYPE, position, prev, actual)
            case None =>
              inferred += tv.name -> actual
          }
        case apf: TypedAST.AppliedClassType =>
          def unifyWithApplied(apa: TypedAST.AppliedClassType): Unit =
            if (apf.raw eq apa.raw) && apf.typeArguments.length == apa.typeArguments.length then
              var i = 0
              while (i < apf.typeArguments.length) {
                unify(apf.typeArguments(i), apa.typeArguments(i), position)
                i += 1
              }

          actual match
            case apa: TypedAST.AppliedClassType =>
              if (apf.raw eq apa.raw) then unifyWithApplied(apa)
              else
                val views = AppliedTypeViews.collectAppliedViewsFrom(apa)
                views.get(apf.raw) match
                  case Some(view) => unifyWithApplied(view)
                  case None =>
            case ct: ClassType =>
              val views = AppliedTypeViews.collectAppliedViewsFrom(ct)
              views.get(apf.raw) match
                case Some(view) => unifyWithApplied(view)
                case None =>
            case _ =>
        case aft: ArrayType =>
          actual match {
            case aat: ArrayType if aft.dimension == aat.dimension =>
              unify(aft.component, aat.component, position)
            case _ =>
          }
        case _ =>
    }

    val formalArgs =
      method.arguments.map(t => TypeSubstitution.substituteType(t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false))
    var i = 0
    while (i < formalArgs.length && i < args.length) {
      unify(formalArgs(i), args(i).`type`, callNode)
      i += 1
    }

    if (expectedReturn != null) {
      val formalReturn =
        TypeSubstitution.substituteType(method.returnType, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false)
      unify(formalReturn, expectedReturn, callNode)
    }

    for (tp <- typeParams) {
      val name = tp.name
      val bound0 = bounds(name)
      val bound =
        upperConstraints.get(name) match
          case None => bound0
          case Some(upper) =>
            if (isSuperTypeForBounds(bound0, upper)) upper
            else if (isSuperTypeForBounds(upper, bound0)) bound0
            else {
              report(INCOMPATIBLE_TYPE, callNode, bound0, upper)
              bound0
            }

      val inferredType0 =
        inferred.get(name)
          .orElse(lowerConstraints.get(name))
          .getOrElse(bound)

      val inferredType =
        if (!isAssignableForBounds(bound, inferredType0)) {
          report(INCOMPATIBLE_TYPE, callNode, bound, inferredType0)
          bound
        } else {
          inferredType0
        }

      inferred += name -> inferredType
    }

    inferred.toMap
  }
}
