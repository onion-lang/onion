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

  /** Check if a closure expression has any untyped parameters */
  private def hasUntypedParams(closure: AST.ClosureExpression): Boolean =
    closure.args.exists(_.typeRef == null)

  /** Check if an expression is a closure with untyped parameters */
  private def isClosureWithUntypedParams(expr: AST.Expression): Boolean =
    expr match {
      case c: AST.ClosureExpression => hasUntypedParams(c)
      case _ => false
    }

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

  private def prepareCallParams(
    node: AST.Node,
    args: Seq[AST.Expression],
    method: Method,
    params: Array[Term],
    expectedArgs: Array[Type]
  ): Option[Array[Term]] = {
    val processedOpt =
      if (method.isVararg) {
        processVarargParamsWithExpected(node, method, params, expectedArgs)
      } else {
        processParamsWithArgs(args, params, expectedArgs)
      }

    processedOpt.flatMap { processedParams =>
      if (method.isVararg) Some(processedParams)
      else fillDefaultArguments(processedParams, method)
    }
  }

  private def isAssignableWithBoxing(target: Type, source: Type): Boolean = {
    if (TypeRules.isAssignable(target, source)) return true

    if (!target.isBasicType && source.isBasicType) {
      val basicType = source.asInstanceOf[BasicType]
      if (basicType == BasicType.VOID) return false
      val boxedType = Boxing.boxedType(table_, basicType)
      return TypeRules.isAssignable(target, boxedType)
    }

    if (target.isBasicType && !source.isBasicType) {
      val targetBasicType = target.asInstanceOf[BasicType]
      if (targetBasicType == BasicType.VOID) return false
      val boxedType = Boxing.boxedType(table_, targetBasicType)
      return TypeRules.isAssignable(boxedType, source)
    }

    false
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
    val methodNames = Array(name, getter(name), getterBoolean(name))
    var methodIndex = 0
    while (methodIndex < methodNames.length) {
      val methodName = methodNames(methodIndex)
      tryFindMethod(node, targetType, methodName, Array.empty) match {
        case Right(method) =>
          val call = new Call(target, method, Array.empty)
          return TypeSubst.withCastOpt(call, TypeSubst.withClassOnly(method.returnType, target.`type`))
        case Left(false) => return None
        case Left(true) =>
      }
      methodIndex += 1
    }
    // None of the method patterns matched
    if (field == null) report(FIELD_NOT_FOUND, node, targetType, node.name)
    else report(FIELD_NOT_ACCESSIBLE, node, targetType, node.name, definition_)
    None
  }

  def typeMethodCall(node: AST.MethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    var target = typed(node.target, context).getOrElse(null)
    if (target == null) return None

    // Check for closures with untyped parameters - they need bidirectional type inference
    val untypedClosureIndices = node.args.zipWithIndex.collect {
      case (expr, i) if isClosureWithUntypedParams(expr) => i
    }.toSet

    // Only type non-closure arguments here if there are untyped closures
    val params = if (untypedClosureIndices.isEmpty) {
      typedTerms(node.args.toArray, context)
    } else {
      // For bidirectional inference, we'll type parameters inside typeMethodCallOnObject
      null
    }

    // If params is null due to typing error (not due to bidirectional inference), return None
    if (params == null && untypedClosureIndices.isEmpty) return None

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
      case wildcardType: TypedAST.WildcardType =>
        // ワイルドカード型は上限境界型として扱い、メソッド呼び出しを許可
        wildcardType.upperBound match {
          case objType: ObjectType =>
            // Cast target to upper bound type for method resolution
            val castedTarget = new TypedAST.AsInstanceOf(target, objType)
            return typeMethodCallOnObject(node, castedTarget, objType, params, context, expected)
          case _ =>
            report(INVALID_METHOD_CALL_TARGET, node, target.`type`)
            return None
        }
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

    // Bidirectional type inference for closures with untyped parameters
    // If params is null, it means we need bidirectional inference
    if (params == null) {
      val untypedClosureIndices = node.args.zipWithIndex.collect {
        case (expr, i) if isClosureWithUntypedParams(expr) => i
      }.toSet
      return typeMethodCallWithBidirectionalInference(node, target, targetType, context, expected, untypedClosureIndices)
    }

    val methods = MethodResolution.findMethods(targetType, name, params, table_)
    if (methods.length == 0) {
      // Try extension methods when normal method not found
      return tryExtensionMethodCall(node, target, targetType, params, context, expected)
    }

    selectSingleMethod(node, targetType, name, methods, types(params)) match {
      case None => None
      case Some(method) if (method.modifier & AST.M_STATIC) != 0 =>
        report(ILLEGAL_METHOD_CALL, node, method.affiliation, name, method.arguments)
        None
      case Some(method) =>
        val classSubst = TypeSubstitution.classSubstitution(target.`type`)
        for {
          methodSubst <- resolveMethodTypeArgs(node, method, params, node.typeArgs, classSubst, expected)
          expectedArgs = TypeSubst.args(method, classSubst, methodSubst)
          finalParams <- prepareCallParams(node, node.args, method, params, expectedArgs)
        } yield {
          val call = new Call(target, method, finalParams)
          val castType = TypeSubst(method.returnType, classSubst, methodSubst)
          TypeSubst.withCast(call, castType)
        }
    }
  }

  /**
   * Try to resolve a method call using extension methods.
   * Extension methods are called as static methods on the container class with the receiver as the first argument.
   */
  private def tryExtensionMethodCall(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    params: Array[Term],
    context: LocalContext,
    expected: Type
  ): Option[Term] = {
    val name = node.name

    // Get the FQCN for extension method lookup
    val receiverFqcn = targetType match {
      case ct: ClassType => ct.name
      case _ => return reportMethodNotFound(node, targetType, name, params)
    }

    // Look up extension methods for this receiver type
    val extensionMethods = lookupExtensionMethods(receiverFqcn)

    // Also check superclasses and interfaces for extension methods
    val allExtensionMethods = collectExtensionMethods(targetType, name)

    if (allExtensionMethods.isEmpty) {
      return reportMethodNotFound(node, targetType, name, params)
    }

    // Find applicable extension methods (matching name and argument types)
    val applicable = allExtensionMethods.filter { extMethod =>
      extMethod.name == name && isExtensionMethodApplicable(extMethod, params)
    }

    if (applicable.isEmpty) {
      return reportMethodNotFound(node, targetType, name, params)
    }

    if (applicable.length > 1) {
      report(AMBIGUOUS_METHOD, node,
        Array[AnyRef](applicable(0).containerClass, name, applicable(0).arguments),
        Array[AnyRef](applicable(1).containerClass, name, applicable(1).arguments)
      )
      return None
    }

    val extMethod = applicable.head

    // Build the static call: Container.method(receiver, args...)
    val containerClass = extMethod.containerClass
    val staticArgs = Array(target) ++ params

    // Find the actual static method in the container class
    val staticMethods = containerClass.findMethod(name, staticArgs)
    if (staticMethods.isEmpty) {
      return reportMethodNotFound(node, targetType, name, params)
    }

    val staticMethod = staticMethods(0)
    val classSubst = TypeSubstitution.classSubstitution(containerClass)

    for {
      methodSubst <- resolveMethodTypeArgs(node, staticMethod, staticArgs, node.typeArgs, classSubst, expected)
      expectedArgs = TypeSubst.args(staticMethod, classSubst, methodSubst)
      processedParams <- processParamsWithExpected(node, staticArgs, expectedArgs)
    } yield {
      val call = new CallStatic(containerClass, staticMethod, processedParams)
      val castType = TypeSubst(staticMethod.returnType, classSubst, methodSubst)
      TypeSubst.withCast(call, castType)
    }
  }

  private def collectExtensionMethods(targetType: ObjectType, name: String): Seq[ExtensionMethodDefinition] = {
    val result = scala.collection.mutable.Buffer[ExtensionMethodDefinition]()

    def collect(tp: ObjectType): Unit = {
      if (tp == null) return
      tp match {
        case ct: ClassType =>
          result ++= lookupExtensionMethods(ct.name).filter(_.name == name)
        case _ =>
      }
      collect(tp.superClass)
      tp.interfaces.foreach(collect)
    }

    collect(targetType)
    result.toSeq
  }

  private def isExtensionMethodApplicable(extMethod: ExtensionMethodDefinition, params: Array[Term]): Boolean = {
    val expectedArgs = extMethod.arguments
    if (params.length != expectedArgs.length) return false

    params.indices.forall { i =>
      isAssignableWithBoxing(expectedArgs(i), params(i).`type`)
    }
  }

  private def reportMethodNotFound(node: AST.Node, targetType: ObjectType, name: String, params: Array[Term]): Option[Term] = {
    report(METHOD_NOT_FOUND, node, targetType, name, types(params))
    None
  }

  /**
   * Bidirectional type inference for method calls with closures that have untyped parameters.
   *
   * The flow is:
   * 1. Type all non-closure arguments normally
   * 2. Use placeholder types for closure arguments (suppress errors)
   * 3. Resolve the method using the non-closure argument types
   * 4. Get the expected types for all arguments from the resolved method
   * 5. Re-type the closures with the expected types
   */
  private def typeMethodCallWithBidirectionalInference(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    context: LocalContext,
    expected: Type,
    untypedClosureIndices: Set[Int]
  ): Option[Term] = {
    val name = node.name
    val args = node.args.toArray

    // Phase 1: Type non-closure arguments, use placeholder for closures
    val preliminaryParams = new Array[Term](args.length)
    var hasNonClosureError = false

    for (i <- args.indices) {
      if (untypedClosureIndices.contains(i)) {
        // Create a placeholder for the closure - we'll type it later
        // For method resolution, we need a rough type hint
        preliminaryParams(i) = null
      } else {
        typed(args(i), context) match {
          case Some(term) => preliminaryParams(i) = term
          case None =>
            hasNonClosureError = true
            preliminaryParams(i) = null
        }
      }
    }

    if (hasNonClosureError) return None

    // Phase 2: Find candidate methods using non-closure arguments
    // We need to find methods that could potentially match
    val candidates = new JTreeSet[Method](new MethodComparator)
    collectMethodsMatching(targetType, name, candidates, isInstanceMethod)

    if (candidates.isEmpty) {
      report(METHOD_NOT_FOUND, node, targetType, name, Array[Type]())
      return None
    }

    // Filter candidates by argument count and non-closure argument types
    val nonClosureTypes = preliminaryParams.zipWithIndex.collect {
      case (term, i) if term != null => (i, term.`type`)
    }.toMap

    val applicableMethods = candidates.asScala.filter { method =>
      val methodArgCount = method.arguments.length
      val argsCount = args.length

      // Check argument count (considering default args and varargs)
      val countOk = if (method.isVararg) {
        argsCount >= method.minArguments
      } else {
        argsCount >= method.minArguments && argsCount <= methodArgCount
      }

      if (!countOk) false
      else {
        // Check non-closure arguments are compatible
        val classSubst = TypeSubstitution.classSubstitution(target.`type`)
        val methodSubst = GenericMethodTypeArguments.infer(
          typing, node, method, preliminaryParams.filter(_ != null), classSubst, expected
        )
        val expectedArgs = TypeSubst.args(method, classSubst, methodSubst)

        nonClosureTypes.forall { case (i, argType) =>
          i < expectedArgs.length && isAssignableWithBoxing(expectedArgs(i), argType)
        }
      }
    }.toArray

    if (applicableMethods.isEmpty) {
      val nonClosureTypesArr = nonClosureTypes.values.toArray
      report(METHOD_NOT_FOUND, node, targetType, name, nonClosureTypesArr)
      return None
    }

    // For now, just pick the first applicable method
    // TODO: Better overload resolution considering closure types
    val method = applicableMethods.head
    val classSubst = TypeSubstitution.classSubstitution(target.`type`)

    // Phase 3: Get preliminary expected argument types from the resolved method
    // First infer method type args using only the non-closure arguments
    // Use inferWithoutDefaults so that type parameters only appearing in closure return types
    // are NOT included in the substitution, allowing closure typing to infer them
    val nonClosureParams = preliminaryParams.filter(_ != null)
    val preliminaryMethodSubst = GenericMethodTypeArguments.inferWithoutDefaults(typing, node, method, nonClosureParams, classSubst, expected)
    // For closure arguments, preserve type variables so closure typing can infer return types
    // Use defaultToBound = false to keep type variables like U in Function1<String, Future<U>>
    val preliminaryExpectedArgs = method.arguments.map { tp =>
      TypeSubstitution.substituteType(tp, classSubst, preliminaryMethodSubst, defaultToBound = false)
    }

    // Phase 4: Type the closures with preliminary expected types
    // The closure will infer its return type from its body
    val finalParams = new Array[Term](args.length)
    var hasError = false

    for (i <- args.indices) {
      if (untypedClosureIndices.contains(i)) {
        val expectedType = if (i < preliminaryExpectedArgs.length) preliminaryExpectedArgs(i) else null
        typed(args(i), context, expectedType) match {
          case Some(term) => finalParams(i) = term
          case None =>
            hasError = true
            finalParams(i) = null
        }
      } else {
        finalParams(i) = preliminaryParams(i)
      }
    }

    if (hasError) return None

    // Check if the method is static (not allowed for instance method call)
    if ((method.modifier & AST.M_STATIC) != 0) {
      report(ILLEGAL_METHOD_CALL, node, method.affiliation, name, method.arguments)
      return None
    }

    // Phase 5: Re-infer method type arguments with actual closure types
    // This allows the method's return type parameter (e.g., U in map<U>) to be inferred from closure's actual return type
    val finalMethodSubst = GenericMethodTypeArguments.infer(typing, node, method, finalParams, classSubst, expected)
    val finalExpectedArgs = TypeSubst.args(method, classSubst, finalMethodSubst)

    // Phase 6: Build the final call
    for {
      finalProcessedParams <- prepareCallParams(node, node.args, method, finalParams, finalExpectedArgs)
    } yield {
      val call = new Call(target, method, finalProcessedParams)
      val castType = TypeSubst(method.returnType, classSubst, finalMethodSubst)
      TypeSubst.withCast(call, castType)
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

      for {
        params <- processNamedArguments(node, node.args, method, context)
        methodSubst <- resolveMethodTypeArgs(node, method, params, node.typeArgs, classSubst, expected)
        expectedArgs = TypeSubst.args(method, classSubst, methodSubst)
        processedParams <- processParamsWithExpected(node, params, expectedArgs)
      } yield {
        val call = new Call(target, method, processedParams)
        val castType = TypeSubst(method.returnType, classSubst, methodSubst)
        TypeSubst.withCast(call, castType)
      }
    }
  }

  private def buildUnqualifiedCall(
    targetType: ClassType,
    method: Method,
    params: Array[Term],
    classSubst: scala.collection.immutable.Map[String, Type],
    methodSubst: scala.collection.immutable.Map[String, Type],
    context: LocalContext
  ): Term = {
    val call =
      if ((method.modifier & AST.M_STATIC) != 0) {
        new CallStatic(targetType, method, params)
      } else if (context.isClosure) {
        new Call(new OuterThis(targetType), method, params)
      } else {
        new Call(new This(targetType), method, params)
      }
    val castType = TypeSubst(method.returnType, classSubst, methodSubst)
    TypeSubst.withCast(call, castType)
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
      for {
        methodSubst <- resolveMethodTypeArgs(node, method, params, node.typeArgs, classSubst, expected)
        expectedArgs = TypeSubst.args(method, classSubst, methodSubst)
        finalParams <- prepareCallParams(node, node.args, method, params, expectedArgs)
      } yield buildUnqualifiedCall(targetType, method, finalParams, classSubst, methodSubst, context)
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

      for {
        params <- processNamedArguments(node, node.args, method, context)
        methodSubst <- resolveMethodTypeArgs(node, method, params, node.typeArgs, classSubst, expected)
        expectedArgs = TypeSubst.args(method, classSubst, methodSubst)
        processedParams <- processParamsWithExpected(node, params, expectedArgs)
      } yield buildUnqualifiedCall(targetType, method, processedParams, classSubst, methodSubst, context)
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
        val allAssignable = params.indices.forall(i => isAssignableWithBoxing(expectedArgs(i), params(i).`type`))
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
          for {
            methodSubst <- GenericMethodTypeArguments.explicit(typing, node, method, node.typeArgs, classSubst)
            expectedArgs = TypeSubst.args(method, classSubst, methodSubst)
            _ <- if (method.isVararg) Some(()) else processParamsWithArgs(node.args, parameters, expectedArgs).map(_ => ())
            term <- makeStaticCall(typeRef, method, parameters, classSubst, methodSubst)
          } yield term
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
                isAssignableWithBoxing(expectedArgs(i), parameters(i).`type`)
              }
              // Check vararg portion
              val varargMatch = if (parameters.length == expectedArgs.length) {
                val lastParamType = parameters.last.`type`
                // Either direct array pass or single element
                (lastParamType.isArrayType && TypeRules.isSuperType(varargType, lastParamType)) ||
                isAssignableWithBoxing(componentType, lastParamType)
              } else if (parameters.length > expectedArgs.length) {
                // Multiple vararg elements
                (fixedArgCount until parameters.length).forall { i =>
                  isAssignableWithBoxing(componentType, parameters(i).`type`)
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
              parameters.indices.forall(i => isAssignableWithBoxing(expectedArgs(i), parameters(i).`type`))
            )(Applicable(method, expectedArgs, methodSubst))
          }
        }.toList

        def finalizeSelection(selected: Applicable): Option[Term] = {
          val classSubst = TypeSubstitution.classSubstitution(typeRef)
          val paramCheck =
            if (selected.method.isVararg) Some(()) else processParamsWithArgs(node.args, parameters, selected.expectedArgs).map(_ => ())
          for {
            _ <- paramCheck
            term <- makeStaticCall(typeRef, selected.method, parameters, classSubst, selected.methodSubst)
          } yield term
        }

        if (applicable.isEmpty) {
          report(METHOD_NOT_FOUND, node, typeRef, node.name, types(parameters))
          None
        } else if (applicable.length == 1) {
          finalizeSelection(applicable.head)
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
            finalizeSelection(best)
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

      for {
        params <- processNamedArguments(node, node.args, method, context)
        methodSubst <- resolveMethodTypeArgs(node, method, params, node.typeArgs, classSubst, expected)
        expectedArgs = TypeSubst.args(method, classSubst, methodSubst)
        processedParams <- processParamsWithExpected(node, params, expectedArgs)
      } yield {
        val call = new CallStatic(typeRef, method, processedParams)
        val castType = TypeSubst(method.returnType, classSubst, methodSubst)
        TypeSubst.withCast(call, castType)
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
        for {
          methodSubst <- resolveMethodTypeArgs(node, method, parameters, node.typeArgs, classSubst, expected)
          expectedArgs = TypeSubst.args(method, classSubst, methodSubst)
          _ <- processParamsWithArgs(node.args, parameters, expectedArgs).map(_ => ())
          finalParams <- fillDefaultArguments(parameters, method)
        } yield {
          val call = new CallSuper(new This(contextClass), method, finalParams)
          val castType = TypeSubst(method.returnType, classSubst, methodSubst)
          TypeSubst.withCast(call, castType)
        }
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
      var i = params.length
      while (i < argsWithDefaults.length) {
        argsWithDefaults(i).defaultValue match {
          case Some(term) => result(i) = term
          case None => return None
        }
        i += 1
      }
      Some(result)
    }
  }
}
