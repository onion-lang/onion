package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing

import java.util.{TreeSet => JTreeSet}

import scala.jdk.CollectionConverters.*

/** Well-known method names used in method resolution */
private object MethodNames {
  val LENGTH = "length"
  val SIZE = "size"
  val GET_PREFIX = "get"
  val IS_PREFIX = "is"
}

/** Type substitution helpers to reduce boilerplate */
private object TypeSubst {
  import scala.collection.immutable.Map

  /** Substitute type with only class-level type parameters from target type */
  def withClassOnly(typ: TypedAST.Type, targetType: TypedAST.Type): TypedAST.Type =
    TypeSubstitution.substituteType(
      typ,
      TypeSubstitution.classSubstitution(targetType),
      Map.empty,
      defaultToBound = true
    )

  /** Substitute type with both class and method type parameters */
  def apply(typ: TypedAST.Type, classSubst: Map[String, TypedAST.Type], methodSubst: Map[String, TypedAST.Type]): TypedAST.Type =
    TypeSubstitution.substituteType(typ, classSubst, methodSubst, defaultToBound = true)

  /** Substitute all argument types of a method */
  def args(method: TypedAST.Method, classSubst: Map[String, TypedAST.Type], methodSubst: Map[String, TypedAST.Type]): Array[TypedAST.Type] =
    method.arguments.map(tp => apply(tp, classSubst, methodSubst))

  /** Wrap term in AsInstanceOf if types differ, otherwise return as-is */
  def withCast(term: Term, targetType: TypedAST.Type): Term =
    if (targetType eq term.`type`) term else new AsInstanceOf(term, targetType)

  /** Option-returning version of withCast */
  def withCastOpt(term: Term, targetType: TypedAST.Type): Option[Term] =
    Some(withCast(term, targetType))
}

final class MethodCallTyping(private val typing: Typing, private val body: TypingBodyPass) {
  import typing.*

  private sealed trait StaticImportLookup
  private case class StaticImportFound(term: Term) extends StaticImportLookup
  private case object StaticImportNotFound extends StaticImportLookup
  private case object StaticImportError extends StaticImportLookup

  private sealed trait StaticImportResolution
  private case class StaticImportResolved(method: Method, term: Term) extends StaticImportResolution
  private case class StaticImportAmbiguous(first: Method, second: Method) extends StaticImportResolution
  private case object StaticImportNoMatch extends StaticImportResolution
  private case class StaticApplicable(method: Method, expectedArgs: Array[Type], methodSubst: scala.collection.immutable.Map[String, Type])

  /** Report ambiguous method error with standard format */
  private def reportAmbiguousMethod(node: AST.Node, methods: Array[Method], name: String): Unit =
    report(
      AMBIGUOUS_METHOD,
      node,
      Array[AnyRef](methods(0).affiliation, name, methods(0).arguments),
      Array[AnyRef](methods(1).affiliation, name, methods(1).arguments)
    )

  /** Select a single method, reporting errors if none found or ambiguous */
  private def selectSingleMethod(
    node: AST.Node,
    targetType: ObjectType,
    name: String,
    methods: Array[Method],
    argTypes: Array[Type]
  ): Option[Method] = methods match {
    case Array() =>
      report(METHOD_NOT_FOUND, node, targetType, name, argTypes)
      None
    case Array(m) => Some(m)
    case _ =>
      reportAmbiguousMethod(node, methods, name)
      None
  }

  /** Resolve method type arguments - explicit if provided, otherwise inferred */
  private def resolveMethodTypeArgs(
    node: AST.Node,
    method: Method,
    params: Array[Term],
    typeArgs: List[AST.TypeNode],
    classSubst: scala.collection.immutable.Map[String, Type],
    expected: Type
  ): Option[scala.collection.immutable.Map[String, Type]] =
    if (typeArgs.nonEmpty)
      GenericMethodTypeArguments.explicit(typing, node, method, typeArgs, classSubst)
    else
      Some(GenericMethodTypeArguments.infer(typing, node, method, params, classSubst, expected))

  /** Collects methods matching the filter from a type hierarchy into candidates set */
  private def collectMethodsMatching(
    tp: ObjectType,
    name: String,
    candidates: JTreeSet[Method],
    filter: Method => Boolean
  ): Unit = {
    def collect(t: ObjectType): Unit = {
      if (t == null) return
      t.methods(name).foreach { m =>
        if (filter(m)) candidates.add(m)
      }
      collect(t.superClass)
      t.interfaces.foreach(collect)
    }
    collect(tp)
  }

  /** Filter for instance (non-static) methods */
  private def isInstanceMethod(m: Method): Boolean = (m.modifier & AST.M_STATIC) == 0

  /** Filter for static methods */
  private def isStaticMethod(m: Method): Boolean = (m.modifier & AST.M_STATIC) != 0

  /** Information extracted from named arguments */
  private case class NamedArgInfo(namedArgNames: Set[String], positionalCount: Int)

  /** Extract named argument information from expression list */
  private def extractNamedArgInfo(args: Seq[AST.Expression]): NamedArgInfo = {
    val namedArgNames = args.collect { case na: AST.NamedArgument => na.name }.toSet
    val positionalCount = args.takeWhile(!_.isInstanceOf[AST.NamedArgument]).size
    NamedArgInfo(namedArgNames, positionalCount)
  }

  /** Filter methods by named argument compatibility */
  private def filterByNamedArgs(candidates: JTreeSet[Method], info: NamedArgInfo): List[Method] =
    candidates.asScala.filter { method =>
      val paramNames = method.argumentsWithDefaults.map(_.name).toSet
      info.namedArgNames.subsetOf(paramNames) && info.positionalCount <= method.arguments.length
    }.toList

  /** Process parameters with type checking, returns None on error */
  private def processParamsWithExpected(
    node: AST.Node,
    params: Array[Term],
    expectedArgs: Array[Type]
  ): Option[Array[Term]] = {
    val results = params.zipWithIndex.map { case (param, i) =>
      processAssignable(node, expectedArgs(i), param)
    }
    if (results.contains(null)) None else Some(results)
  }

  /** Process parameters with args and expected types, returns None on error */
  private def processParamsWithArgs(
    args: Seq[AST.Expression],
    params: Array[Term],
    expectedArgs: Array[Type]
  ): Option[Array[Term]] = {
    val results = params.zipWithIndex.map { case (param, i) =>
      processAssignable(args(i), expectedArgs(i), param)
    }
    if (results.contains(null)) None else Some(results)
  }

  /**
   * Wrap parameters for vararg method call.
   * If the method is vararg, wraps trailing arguments into an array.
   * Returns the adjusted parameters array.
   */
  private def wrapVarargParams(
    method: Method,
    params: Array[Term],
    expectedArgs: Array[Type]
  ): Array[Term] = {
    if (!method.isVararg || expectedArgs.isEmpty) return params

    val fixedArgCount = expectedArgs.length - 1
    val varargType = expectedArgs.last.asInstanceOf[ArrayType]
    val componentType = varargType.base

    if (params.length == expectedArgs.length) {
      // Check if last param is already the correct array type
      val lastParamType = params.last.`type`
      if (lastParamType.isArrayType && TypeRules.isSuperType(varargType, lastParamType)) {
        // Direct array pass - no wrapping needed
        return params
      }
    }

    // Wrap vararg elements into array
    val fixedParams = params.take(fixedArgCount)
    val varargElements = params.drop(fixedArgCount)
    val arrayTerm = new NewArrayWithValues(varargType, varargElements)
    fixedParams :+ arrayTerm
  }

  /**
   * Process parameters for vararg method with type checking.
   * Handles both fixed and vararg portions of the parameter list.
   */
  private def processVarargParamsWithExpected(
    node: AST.Node,
    method: Method,
    params: Array[Term],
    expectedArgs: Array[Type]
  ): Option[Array[Term]] = {
    if (!method.isVararg || expectedArgs.isEmpty) {
      return processParamsWithExpected(node, params, expectedArgs)
    }

    val fixedArgCount = expectedArgs.length - 1
    val varargType = expectedArgs.last.asInstanceOf[ArrayType]
    val componentType = varargType.base

    // Process fixed parameters
    val fixedResults = (0 until fixedArgCount).map { i =>
      if (i < params.length) processAssignable(node, expectedArgs(i), params(i)) else null
    }.toArray

    if (fixedResults.contains(null)) return None

    // Process vararg portion
    if (params.length == expectedArgs.length) {
      // Could be direct array pass or single element
      val lastParam = params.last
      val lastParamType = lastParam.`type`
      if (lastParamType.isArrayType && TypeRules.isSuperType(varargType, lastParamType)) {
        // Direct array pass
        val processedLast = processAssignable(node, varargType, lastParam)
        if (processedLast == null) return None
        return Some(fixedResults :+ processedLast)
      }
    }

    // Wrap vararg elements
    val varargElements = params.drop(fixedArgCount).map { param =>
      processAssignable(node, componentType, param)
    }
    if (varargElements.contains(null)) return None

    val arrayTerm = new NewArrayWithValues(varargType, varargElements)
    Some(fixedResults :+ arrayTerm)
  }

  def typeMemberSelection(node: AST.MemberSelection, context: LocalContext): Option[Term] = {
    val contextClass = definition_
    var target = typed(node.target, context).getOrElse(null)
    if (target == null) return None
    if (target.`type`.isNullType) {
      report(INCOMPATIBLE_TYPE, node.target, rootClass, target.`type`)
      return None
    }

    // プリミティブ型の場合はボクシング
    if (target.`type`.isBasicType) {
      val basicType = target.`type`.asInstanceOf[BasicType]
      if (basicType == BasicType.VOID) {
        report(INCOMPATIBLE_TYPE, node.target, rootClass, basicType)
        return None
      }
      target = Boxing.boxing(table_, target)
    }

    val targetType = target.`type`.asInstanceOf[ObjectType]
    if (!MemberAccess.ensureTypeAccessible(typing, node, targetType, contextClass)) return None
    val name = node.name
    if (target.`type`.isArrayType) {
      if (name.equals(MethodNames.LENGTH) || name.equals(MethodNames.SIZE)) {
        return Some(new ArrayLength(target))
      } else {
        return None
      }
    }
    val field = MemberAccess.findField(targetType, name)
    if (field != null && MemberAccess.isMemberAccessible(field, definition_)) {
      val ref = new RefField(target, field)
      return TypeSubst.withCastOpt(ref, TypeSubst.withClassOnly(ref.`type`, target.`type`))
    }

    // Try method name, then getter pattern, then boolean getter pattern
    for (methodName <- Seq(name, getter(name), getterBoolean(name))) {
      tryFindMethod(node, targetType, methodName, Array.empty) match {
        case Right(method) =>
          val call = new Call(target, method, Array.empty)
          return TypeSubst.withCastOpt(call, TypeSubst.withClassOnly(method.returnType, target.`type`))
        case Left(false) => return None
        case Left(true) => // continue to next name
      }
    }
    // None of the method patterns matched
    if (field == null) report(FIELD_NOT_FOUND, node, targetType, node.name)
    else report(FIELD_NOT_ACCESSIBLE, node, targetType, node.name, definition_)
    None
  }

  def typeMethodCall(node: AST.MethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    var target = typed(node.target, context).getOrElse(null)
    if (target == null) return None
    val params = typedTerms(node.args.toArray, context)
    if (params == null) return None
    target.`type` match {
      case targetType: ObjectType =>
        return typeMethodCallOnObject(node, target, targetType, params, context, expected)
      case basicType: BasicType =>
        // オートボクシング: プリミティブ型をラッパークラスに変換
        if (basicType == BasicType.VOID) {
          report(CANNOT_CALL_METHOD_ON_PRIMITIVE, node, basicType, node.name)
          return None
        }
        target = Boxing.boxing(table_, target)
        return typeMethodCallOnObject(node, target, target.`type`.asInstanceOf[ObjectType], params, context, expected)
      case _ =>
        report(INVALID_METHOD_CALL_TARGET, node, target.`type`)
        return None
    }
  }

  private def typeMethodCallOnObject(node: AST.MethodCall, target: Term, targetType: ObjectType, params: Array[Term], context: LocalContext, expected: Type = null): Option[Term] = {
    val name = node.name

    // 名前付き引数がある場合は特別な処理
    if (hasNamedArguments(node.args)) {
      return typeMethodCallWithNamedArgs(node, target, targetType, context, expected)
    }

    val methods = MethodResolution.findMethods(targetType, name, params, table_)
    selectSingleMethod(node, targetType, name, methods, types(params)) match {
      case None => None
      case Some(method) if (method.modifier & AST.M_STATIC) != 0 =>
        report(ILLEGAL_METHOD_CALL, node, method.affiliation, name, method.arguments)
        None
      case Some(method) =>
        val classSubst = TypeSubstitution.classSubstitution(target.`type`)
        val methodSubst = resolveMethodTypeArgs(node, method, params, node.typeArgs, classSubst, expected).getOrElse(return None)

        val expectedArgs = TypeSubst.args(method, classSubst, methodSubst)

        // Handle vararg methods specially
        val processedParams = if (method.isVararg) {
          processVarargParamsWithExpected(node, method, params, expectedArgs).getOrElse(return None)
        } else {
          processParamsWithArgs(node.args, params, expectedArgs) match
            case None => return None
            case Some(processed) => processed
        }

        // デフォルト引数で足りない分を補完
        val finalParams =
          if (method.isVararg) processedParams
          else fillDefaultArguments(processedParams, method).getOrElse(return None)

        val call = new Call(target, method, finalParams)
        val castType = TypeSubst(method.returnType, classSubst, methodSubst)
        TypeSubst.withCastOpt(call, castType)
    }
  }

  private def typeMethodCallWithNamedArgs(node: AST.MethodCall, target: Term, targetType: ObjectType, context: LocalContext, expected: Type): Option[Term] = {
    val name = node.name

    // 名前付き引数がある場合は、全てのメソッドから名前でフィルタリング
    val candidates = new JTreeSet[Method](new MethodComparator)
    collectMethodsMatching(targetType, name, candidates, isInstanceMethod)
    if (candidates.isEmpty) {
      report(METHOD_NOT_FOUND, node, targetType, name, Array[Type]())
      return None
    }

    // 名前付き引数でフィルタリング
    val info = extractNamedArgInfo(node.args)
    val applicable = filterByNamedArgs(candidates, info)

    if (applicable.isEmpty) {
      report(METHOD_NOT_FOUND, node, targetType, name, Array[Type]())
      None
    } else if (applicable.length > 1) {
      report(
        AMBIGUOUS_METHOD,
        node,
        Array[AnyRef](applicable(0).affiliation, name, applicable(0).arguments),
        Array[AnyRef](applicable(1).affiliation, name, applicable(1).arguments)
      )
      None
    } else {
      val method = applicable.head
      val classSubst = TypeSubstitution.classSubstitution(target.`type`)

      // 名前付き引数を含めて処理
      processNamedArguments(node, node.args, method, context) match {
        case Some(params) =>
          val methodSubst = resolveMethodTypeArgs(node, method, params, node.typeArgs, classSubst, expected).getOrElse(return None)

          val expectedArgs = TypeSubst.args(method, classSubst, methodSubst)
          processParamsWithExpected(node, params, expectedArgs) match {
            case Some(processedParams) =>
              val call = new Call(target, method, processedParams)
              val castType = TypeSubst(method.returnType, classSubst, methodSubst)
              TypeSubst.withCastOpt(call, castType)
            case None => None
          }
        case None =>
          None
      }
    }
  }

  def typeUnqualifiedMethodCall(node: AST.UnqualifiedMethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    // 名前付き引数がある場合は特別な処理
    if (hasNamedArguments(node.args)) {
      return typeUnqualifiedMethodCallWithNamedArgs(node, context, expected)
    }

    var params = typedTerms(node.args.toArray, context)
    if (params == null) return None
    val targetType = definition_
    val methods = MethodResolution.findMethods(targetType, node.name, params, table_)
    if (methods.length == 0) {
      resolveStaticImportMethodCall(node, params, expected) match {
        case StaticImportFound(term) =>
          Some(term)
        case StaticImportError =>
          None
        case StaticImportNotFound =>
          resolveCallableValue(node, params, context, expected) match {
            case Some(term) =>
              Some(term)
            case None =>
              report(METHOD_NOT_FOUND, node, targetType, node.name, types(params))
              None
          }
      }
    } else if (methods.length > 1) {
      reportAmbiguousMethod(node, methods, node.name)
      None
    } else {
      val method = methods(0)
      val classSubst: scala.collection.immutable.Map[String, Type] = scala.collection.immutable.Map.empty
      val methodSubst = resolveMethodTypeArgs(node, method, params, node.typeArgs, classSubst, expected).getOrElse(return None)

      val expectedArgs = TypeSubst.args(method, classSubst, methodSubst)

      // Handle vararg methods specially
      val processedParams = if (method.isVararg) {
        processVarargParamsWithExpected(node, method, params, expectedArgs).getOrElse(return None)
      } else {
        processParamsWithArgs(node.args, params, expectedArgs) match
          case None => return None
          case Some(processed) => processed
      }

      // デフォルト引数で足りない分を補完
      val finalParams =
        if (method.isVararg) processedParams
        else fillDefaultArguments(processedParams, method).getOrElse(return None)

      if ((methods(0).modifier & AST.M_STATIC) != 0) {
        val call = new CallStatic(targetType, method, finalParams)
        val castType = TypeSubst(method.returnType, classSubst, methodSubst)
        TypeSubst.withCastOpt(call, castType)
      } else {
        if (context.isClosure) {
          val call = new Call(new OuterThis(targetType), method, finalParams)
          val castType = TypeSubst(method.returnType, classSubst, methodSubst)
          TypeSubst.withCastOpt(call, castType)
        } else {
          val call = new Call(new This(targetType), method, finalParams)
          val castType = TypeSubst(method.returnType, classSubst, methodSubst)
          TypeSubst.withCastOpt(call, castType)
        }
      }
    }
  }

  private def resolveCallableValue(
    node: AST.UnqualifiedMethodCall,
    params: Array[Term],
    context: LocalContext,
    expected: Type
  ): Option[Term] = {
    if (node.typeArgs.nonEmpty) return None

    def callOnTarget(target: Term, targetType: ObjectType): Option[Term] = {
      if (targetType.methods("call").isEmpty) return None
      val callNode = new AST.MethodCall(node.location, new AST.Id(node.location, node.name), "call", node.args, Nil)
      typeMethodCallOnObject(callNode, target, targetType, params, context, expected)
    }

    val local = context.lookup(node.name)
    if (local != null) {
      context.recordUsage(node.name)
      local.tp match
        case targetType: ObjectType => return callOnTarget(new RefLocal(local), targetType)
        case _ => return None
    }

    if (!context.isStatic) {
      val field = MemberAccess.findField(definition_, node.name)
      if (field != null && MemberAccess.isMemberAccessible(field, definition_)) {
        field.`type` match
          case targetType: ObjectType =>
            return callOnTarget(new RefField(new This(definition_), field), targetType)
          case _ =>
      }
    }

    None
  }

  private def typeUnqualifiedMethodCallWithNamedArgs(node: AST.UnqualifiedMethodCall, context: LocalContext, expected: Type): Option[Term] = {
    val targetType = definition_

    // 名前付き引数がある場合は、全てのメソッドから名前でフィルタリング
    val candidates = new JTreeSet[Method](new MethodComparator)
    collectMethodsMatching(targetType, node.name, candidates, _ => true)
    if (candidates.isEmpty) {
      report(METHOD_NOT_FOUND, node, targetType, node.name, Array[Type]())
      return None
    }

    // 名前付き引数でフィルタリング
    val info = extractNamedArgInfo(node.args)
    val applicable = filterByNamedArgs(candidates, info)

    if (applicable.isEmpty) {
      report(METHOD_NOT_FOUND, node, targetType, node.name, Array[Type]())
      None
    } else if (applicable.length > 1) {
      report(
        AMBIGUOUS_METHOD,
        node,
        Array[AnyRef](applicable(0).affiliation, node.name, applicable(0).arguments),
        Array[AnyRef](applicable(1).affiliation, node.name, applicable(1).arguments)
      )
      None
    } else {
      val method = applicable.head
      val classSubst: scala.collection.immutable.Map[String, Type] = scala.collection.immutable.Map.empty

      // 名前付き引数を含めて処理
      processNamedArguments(node, node.args, method, context) match {
        case Some(params) =>
          val methodSubst = resolveMethodTypeArgs(node, method, params, node.typeArgs, classSubst, expected).getOrElse(return None)

          val expectedArgs = TypeSubst.args(method, classSubst, methodSubst)
          processParamsWithExpected(node, params, expectedArgs) match {
            case Some(processedParams) =>
              if ((method.modifier & AST.M_STATIC) != 0) {
                val call = new CallStatic(targetType, method, processedParams)
                val castType = TypeSubst(method.returnType, classSubst, methodSubst)
                TypeSubst.withCastOpt(call, castType)
              } else {
                if (context.isClosure) {
                  val call = new Call(new OuterThis(targetType), method, processedParams)
                  val castType = TypeSubst(method.returnType, classSubst, methodSubst)
                  TypeSubst.withCastOpt(call, castType)
                } else {
                  val call = new Call(new This(targetType), method, processedParams)
                  val castType = TypeSubst(method.returnType, classSubst, methodSubst)
                  TypeSubst.withCastOpt(call, castType)
                }
              }
            case None => None
          }
        case None =>
          None
      }
    }
  }

  private def resolveStaticImportMethodCall(
    node: AST.UnqualifiedMethodCall,
    params: Array[Term],
    expected: Type
  ): StaticImportLookup = {
    val mappedTypeArgs =
      if (node.typeArgs.nonEmpty) {
        mapTypeArgs(node.typeArgs) match {
          case Some(mapped) => Some(mapped)
          case None => return StaticImportError
        }
      } else {
        None
      }

    val resolved = scala.collection.mutable.Buffer[StaticImportResolved]()
    var ambiguous: Option[StaticImportAmbiguous] = None
    staticImportedList_.getItems.foreach { item =>
      val typeRef = load(item.getName)
      if (typeRef != null) {
        resolveStaticImportOnType(node, typeRef, params, expected, mappedTypeArgs) match {
          case found: StaticImportResolved =>
            resolved += found
          case amb: StaticImportAmbiguous =>
            if (ambiguous.isEmpty) ambiguous = Some(amb)
          case StaticImportNoMatch =>
        }
      }
    }

    if (resolved.length == 1) {
      StaticImportFound(resolved.head.term)
    } else if (resolved.length > 1) {
      reportAmbiguousMethod(node, resolved(0).method, resolved(1).method)
      StaticImportError
    } else {
      ambiguous match {
        case Some(amb) =>
          reportAmbiguousMethod(node, amb.first, amb.second)
          StaticImportError
        case None =>
          StaticImportNotFound
      }
    }
  }

  private def resolveStaticImportOnType(
    node: AST.UnqualifiedMethodCall,
    typeRef: ClassType,
    params: Array[Term],
    expected: Type,
    mappedTypeArgs: Option[Array[Type]]
  ): StaticImportResolution = {
    val name = node.name
    val candidates = new JTreeSet[Method](new MethodComparator)
    collectMethodsMatching(typeRef, name, candidates, isStaticMethod)
    if (candidates.isEmpty) return StaticImportNoMatch

    val applicable = candidates.asScala.flatMap { method =>
      val classSubst = TypeSubstitution.classSubstitution(typeRef)
      val methodSubstOpt = mappedTypeArgs match {
        case Some(mapped) =>
          GenericMethodTypeArguments.explicitFromMappedArgs(
            typing,
            node,
            method,
            mapped,
            classSubst,
            reportErrors = false
          )
        case None =>
          Some(GenericMethodTypeArguments.infer(typing, node, method, params, classSubst, expected))
      }
      methodSubstOpt.flatMap { methodSubst =>
        val expectedArgs = TypeSubst.args(method, classSubst, methodSubst)
        // デフォルト引数を考慮: minArguments <= params.length <= expectedArgs.length
        if (params.length < method.minArguments || params.length > expectedArgs.length) None
        else {
          val allAssignable = params.indices.forall(i => TypeRules.isAssignable(expectedArgs(i), params(i).`type`))
          Option.when(allAssignable)(StaticApplicable(method, expectedArgs, methodSubst))
        }
      }
    }.toList

    if (applicable.isEmpty) return StaticImportNoMatch

    selectStaticApplicable(applicable) match {
      case Left(amb) =>
        StaticImportAmbiguous(amb.first, amb.second)
      case Right(chosen) =>
        val classSubst = TypeSubstitution.classSubstitution(typeRef)
        val adjusted = params.indices.map(i => processAssignable(node.args(i), chosen.expectedArgs(i), params(i))).toArray
        if (adjusted.contains(null)) StaticImportNoMatch
        else {
          buildStaticCall(typeRef, chosen.method, adjusted, classSubst, chosen.methodSubst) match {
            case Some(term) => StaticImportResolved(chosen.method, term)
            case None => StaticImportNoMatch
          }
        }
    }
  }

  private def selectStaticApplicable(
    applicable: List[StaticApplicable]
  ): Either[StaticImportAmbiguous, StaticApplicable] = {
    if (applicable.length == 1) return Right(applicable.head)

    def compareApplicable(a1: StaticApplicable, a2: StaticApplicable): Int =
      if TypeRules.isAllSuperType(a2.expectedArgs, a1.expectedArgs) then -1
      else if TypeRules.isAllSuperType(a1.expectedArgs, a2.expectedArgs) then 1
      else 0

    val sorted = applicable.sortWith((a1, a2) => compareApplicable(a1, a2) < 0)
    if (sorted.length < 2) Right(sorted.head)
    else if (compareApplicable(sorted.head, sorted(1)) >= 0) Left(StaticImportAmbiguous(sorted.head.method, sorted(1).method))
    else Right(sorted.head)
  }

  private def mapTypeArgs(typeArgs: List[AST.TypeNode]): Option[Array[Type]] =
    typeArgs.foldLeft(Option(List.empty[Type])) { (accOpt, typeArg) =>
      accOpt.flatMap { acc =>
        val mapped = mapFrom(typeArg)
        if (mapped == null) None
        else if (mapped eq BasicType.VOID) { report(TYPE_ARGUMENT_MUST_BE_REFERENCE, typeArg, mapped.name); None }
        else Some(acc :+ mapped)
      }
    }.map(_.toArray)

  private def buildStaticCall(
    typeRef: ClassType,
    method: Method,
    params: Array[Term],
    classSubst: scala.collection.immutable.Map[String, Type],
    methodSubst: scala.collection.immutable.Map[String, Type]
  ): Option[Term] = {
    // デフォルト引数で足りない分を補完
    fillDefaultArguments(params, method).map { finalParams =>
      val call = new CallStatic(typeRef, method, finalParams)
      val castType = TypeSubst(method.returnType, classSubst, methodSubst)
      TypeSubst.withCast(call, castType)
    }
  }

  private def reportAmbiguousMethod(node: AST.Node, first: Method, second: Method): Unit = {
    report(
      AMBIGUOUS_METHOD,
      node,
      Array[AnyRef](first.affiliation, first.name, first.arguments),
      Array[AnyRef](second.affiliation, second.name, second.arguments)
    )
  }

  def typeStaticMemberSelection(node: AST.StaticMemberSelection): Option[Term] = {
    val typeRef = mapFrom(node.typeRef).asInstanceOf[ClassType]
    if (typeRef == null) return None
    val field = MemberAccess.findField(typeRef, node.name)
    if (field == null) {
      report(FIELD_NOT_FOUND, node, typeRef, node.name)
      None
    } else {
      Some(new RefStaticField(typeRef, field))
    }
  }

  /** Create a CallStatic with type substitution and optional cast */
  private def makeStaticCall(
    typeRef: ClassType,
    method: Method,
    parameters: Array[Term],
    classSubst: scala.collection.immutable.Map[String, Type],
    methodSubst: scala.collection.immutable.Map[String, Type]
  ): Option[Term] =
    val expectedArgs = TypeSubst.args(method, classSubst, methodSubst)
    val wrappedParams = if (method.isVararg) {
      wrapVarargParams(method, parameters, expectedArgs)
    } else {
      parameters
    }
    val finalParamsOpt =
      if (method.isVararg) Some(wrappedParams)
      else fillDefaultArguments(wrappedParams, method)
    finalParamsOpt.map { finalParams =>
      val call = new CallStatic(typeRef, method, finalParams)
      val castType = TypeSubst(method.returnType, classSubst, methodSubst)
      TypeSubst.withCast(call, castType)
    }

  def typeStaticMethodCall(node: AST.StaticMethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    val typeRef = mapFrom(node.typeRef).asInstanceOf[ClassType]
    if (typeRef == null) return None

    // 名前付き引数がある場合は特別な処理
    if (hasNamedArguments(node.args)) {
      return typeStaticMethodCallWithNamedArgs(node, typeRef, context, expected)
    }

    val parameters = typedTerms(node.args.toArray, context)
    if (parameters == null) {
      None
    } else {
      if (node.typeArgs.nonEmpty) {
        val methods = typeRef.findMethod(node.name, parameters)
        if (methods.length == 0) {
          report(METHOD_NOT_FOUND, node, typeRef, node.name, types(parameters))
          None
        } else if (methods.length > 1) {
          report(AMBIGUOUS_METHOD, node, node.name, typeNames(methods(0).arguments), typeNames(methods(1).arguments))
          None
        } else {
          val method = methods(0)
          val classSubst = TypeSubstitution.classSubstitution(typeRef)
          val methodSubst = GenericMethodTypeArguments.explicit(typing, node, method, node.typeArgs, classSubst).getOrElse(return None)

          val expectedArgs = TypeSubst.args(method, classSubst, methodSubst)
          // For vararg methods, skip standard param processing since makeStaticCall handles wrapping
          if (!method.isVararg) {
            processParamsWithArgs(node.args, parameters, expectedArgs) match
              case None => return None
              case Some(_) => ()
          }

          makeStaticCall(typeRef, method, parameters, classSubst, methodSubst)
        }
      } else {
        val candidates = new JTreeSet[Method](new MethodComparator)
        collectMethodsMatching(typeRef, node.name, candidates, isStaticMethod)
        if (candidates.isEmpty) {
          report(METHOD_NOT_FOUND, node, typeRef, node.name, types(parameters))
          return None
        }

        final case class Applicable(method: Method, expectedArgs: Array[Type], methodSubst: scala.collection.immutable.Map[String, Type])

        val applicable = candidates.asScala.flatMap { method =>
          val classSubst = TypeSubstitution.classSubstitution(typeRef)
          val methodSubst = GenericMethodTypeArguments.infer(typing, node, method, parameters, classSubst, expected)
          val expectedArgs = TypeSubst.args(method, classSubst, methodSubst)

          if (method.isVararg && expectedArgs.nonEmpty) {
            // For vararg methods: allow any number of args >= fixedArgCount
            val fixedArgCount = expectedArgs.length - 1
            val varargType = expectedArgs.last.asInstanceOf[ArrayType]
            val componentType = varargType.base

            if (parameters.length < fixedArgCount) None
            else {
              // Check fixed args match
              val fixedMatch = (0 until fixedArgCount).forall { i =>
                TypeRules.isAssignable(expectedArgs(i), parameters(i).`type`)
              }
              // Check vararg portion
              val varargMatch = if (parameters.length == expectedArgs.length) {
                val lastParamType = parameters.last.`type`
                // Either direct array pass or single element
                (lastParamType.isArrayType && TypeRules.isSuperType(varargType, lastParamType)) ||
                  TypeRules.isAssignable(componentType, lastParamType)
              } else if (parameters.length > expectedArgs.length) {
                // Multiple vararg elements
                (fixedArgCount until parameters.length).forall { i =>
                  TypeRules.isAssignable(componentType, parameters(i).`type`)
                }
              } else {
                // No vararg elements (empty array)
                true
              }
              Option.when(fixedMatch && varargMatch)(Applicable(method, expectedArgs, methodSubst))
            }
          } else {
            // Non-vararg method: original logic with default args
            if (parameters.length < method.minArguments || parameters.length > expectedArgs.length) None
            else Option.when(
              parameters.indices.forall(i => TypeRules.isAssignable(expectedArgs(i), parameters(i).`type`))
            )(Applicable(method, expectedArgs, methodSubst))
          }
        }.toList

        if (applicable.isEmpty) {
          report(METHOD_NOT_FOUND, node, typeRef, node.name, types(parameters))
          None
        } else if (applicable.length == 1) {
          val selected = applicable.head
          val classSubst = TypeSubstitution.classSubstitution(typeRef)
          // For vararg methods, skip standard param processing since makeStaticCall handles wrapping
          if (!selected.method.isVararg) {
            processParamsWithArgs(node.args, parameters, selected.expectedArgs) match
              case None => return None
              case Some(_) => ()
          }
          makeStaticCall(typeRef, selected.method, parameters, classSubst, selected.methodSubst)
        } else {
          def compareApplicable(a1: Applicable, a2: Applicable): Int =
            if TypeRules.isAllSuperType(a2.expectedArgs, a1.expectedArgs) then -1
            else if TypeRules.isAllSuperType(a1.expectedArgs, a2.expectedArgs) then 1
            else 0

          val sorted = applicable.sortWith((a1, a2) => compareApplicable(a1, a2) < 0)
          val best = sorted.head
          if (sorted.length >= 2 && compareApplicable(sorted.head, sorted(1)) >= 0) {
            report(AMBIGUOUS_METHOD, node, node.name, typeNames(sorted.head.method.arguments), typeNames(sorted(1).method.arguments))
            None
          } else {
            val classSubst = TypeSubstitution.classSubstitution(typeRef)
            // For vararg methods, skip standard param processing since makeStaticCall handles wrapping
            if (!best.method.isVararg) {
              processParamsWithArgs(node.args, parameters, best.expectedArgs) match
                case None => return None
                case Some(_) => ()
            }
            makeStaticCall(typeRef, best.method, parameters, classSubst, best.methodSubst)
          }
        }
      }
    }
  }

  private def typeStaticMethodCallWithNamedArgs(node: AST.StaticMethodCall, typeRef: ClassType, context: LocalContext, expected: Type): Option[Term] = {
    val candidates = new JTreeSet[Method](new MethodComparator)

    collectMethodsMatching(typeRef, node.name, candidates, isStaticMethod)
    if (candidates.isEmpty) {
      report(METHOD_NOT_FOUND, node, typeRef, node.name, Array[Type]())
      return None
    }

    // ヘルパーメソッドで名前付き引数情報を抽出・フィルタ
    val info = extractNamedArgInfo(node.args)
    val applicable = filterByNamedArgs(candidates, info)

    if (applicable.isEmpty) {
      report(METHOD_NOT_FOUND, node, typeRef, node.name, Array[Type]())
      None
    } else if (applicable.length > 1) {
      report(AMBIGUOUS_METHOD, node, node.name, typeNames(applicable(0).arguments), typeNames(applicable(1).arguments))
      None
    } else {
      val method = applicable.head
      val classSubst = TypeSubstitution.classSubstitution(typeRef)

      // 名前付き引数を含めて処理
      processNamedArguments(node, node.args, method, context) match {
        case Some(params) =>
          val methodSubst = resolveMethodTypeArgs(node, method, params, node.typeArgs, classSubst, expected).getOrElse(return None)

          val expectedArgs = TypeSubst.args(method, classSubst, methodSubst)
          processParamsWithExpected(node, params, expectedArgs) match {
            case Some(processedParams) =>
              val call = new CallStatic(typeRef, method, processedParams)
              val castType = TypeSubst(method.returnType, classSubst, methodSubst)
              TypeSubst.withCastOpt(call, castType)
            case None =>
              None
          }
        case None =>
          None
      }
    }
  }

  def typeSuperMethodCall(node: AST.SuperMethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    val parameters = typedTerms(node.args.toArray, context)
    if (parameters == null) return None
    val contextClass = definition_
    tryFindMethod(node, contextClass.superClass, node.name, parameters) match {
      case Right(method) =>
        val classSubst = TypeSubstitution.classSubstitution(contextClass.superClass)
        val methodSubst = resolveMethodTypeArgs(node, method, parameters, node.typeArgs, classSubst, expected).getOrElse(return None)

        val expectedArgs = TypeSubst.args(method, classSubst, methodSubst)
        processParamsWithArgs(node.args, parameters, expectedArgs) match
          case None => return None
          case Some(_) => ()
        // デフォルト引数で足りない分を補完
        val finalParams = fillDefaultArguments(parameters, method).getOrElse(return None)
        val call = new CallSuper(new This(contextClass), method, finalParams)
        val castType = TypeSubst(method.returnType, classSubst, methodSubst)
        TypeSubst.withCastOpt(call, castType)
      case Left(_) =>
        report(METHOD_NOT_FOUND, node, contextClass, node.name, types(parameters))
        None
    }
  }

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)

  private def typedTerms(nodes: Array[AST.Expression], context: LocalContext): Array[Term] =
    body.typedTerms(nodes, context)

  private def processAssignable(node: AST.Node, expected: Type, term: Term): Term =
    body.processAssignable(node, expected, term)

  private def tryFindMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Either[Boolean, Method] =
    body.tryFindMethod(node, target, name, params)

  private def types(terms: Array[Term]): Array[Type] =
    body.types(terms)

  private def typeNames(types: Array[Type]): Array[String] =
    body.typeNames(types)

  private def getter(name: String): String =
    MethodNames.GET_PREFIX + Character.toUpperCase(name.charAt(0)) + name.substring(1)

  private def getterBoolean(name: String): String =
    MethodNames.IS_PREFIX + Character.toUpperCase(name.charAt(0)) + name.substring(1)

  /**
   * 名前付き引数を含む引数リストを並べ替えて型付けする
   * @return 成功時は Some(並べ替えられた引数配列), エラー時は None
   */
  private def processNamedArguments(
    node: AST.Node,
    args: List[AST.Expression],
    method: Method,
    context: LocalContext
  ): Option[Array[Term]] = {
    val argsWithDefaults = method.argumentsWithDefaults
    val paramNames = argsWithDefaults.map(_.name)
    val result = new Array[Term](argsWithDefaults.length)
    val filled = new Array[Boolean](argsWithDefaults.length)

    var positionalIndex = 0
    var sawNamed = false
    var hasError = false

    // 位置引数と名前付き引数を処理
    args.foreach { arg =>
      arg match {
        case named: AST.NamedArgument =>
          sawNamed = true
          // パラメータ名を検索
          val paramIndex = paramNames.indexOf(named.name)
          if (paramIndex < 0) {
            report(UNKNOWN_PARAMETER_NAME, named, named.name)
            hasError = true
          } else if (filled(paramIndex)) {
            report(DUPLICATE_ARGUMENT, named, named.name)
            hasError = true
          } else {
            // 値を型付け
            typed(named.value, context) match {
              case Some(term) =>
                result(paramIndex) = term
                filled(paramIndex) = true
              case None =>
                hasError = true
            }
          }

        case expr =>
          // 位置引数
          if (sawNamed) {
            report(POSITIONAL_AFTER_NAMED, expr)
            hasError = true
          } else if (positionalIndex >= argsWithDefaults.length) {
            // 引数が多すぎる - これは別のエラーで処理される
            typed(expr, context) // 型付けだけして結果は無視
            positionalIndex += 1
          } else {
            typed(expr, context) match {
              case Some(term) =>
                result(positionalIndex) = term
                filled(positionalIndex) = true
                positionalIndex += 1
              case None =>
                hasError = true
                positionalIndex += 1
            }
          }
      }
    }

    if (hasError) return None

    // 必須引数が全て指定されているか確認
    val missingRequired = argsWithDefaults.indices.find(i => !filled(i) && argsWithDefaults(i).defaultValue.isEmpty)
    if (missingRequired.isDefined) {
      report(METHOD_NOT_FOUND, node, method.affiliation, method.name, argsWithDefaults.map(_.argType))
      return None
    }

    // 足りない引数をデフォルト値で補完
    argsWithDefaults.indices.foreach { i =>
      if (!filled(i)) {
        result(i) = argsWithDefaults(i).defaultValue.get
        filled(i) = true
      }
    }

    Some(result)
  }

  /**
   * 引数リストに名前付き引数が含まれているか確認
   */
  private def hasNamedArguments(args: List[AST.Expression]): Boolean =
    args.exists(_.isInstanceOf[AST.NamedArgument])

  /**
   * デフォルト引数で足りない分を補完する
   */
  private def fillDefaultArguments(params: Array[Term], method: Method): Option[Array[Term]] = {
    val argsWithDefaults = method.argumentsWithDefaults
    if (params.length >= argsWithDefaults.length) {
      Some(params)
    } else {
      val result = new Array[Term](argsWithDefaults.length)
      System.arraycopy(params, 0, result, 0, params.length)
      (params.length until argsWithDefaults.length).foreach { i =>
        argsWithDefaults(i).defaultValue match {
          case Some(term) => result(i) = term
          case None => return None
        }
      }
      Some(result)
    }
  }
}
