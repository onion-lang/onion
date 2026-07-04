package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

import ArgumentHelpers.hasNamedArguments
import java.util.{TreeSet => JTreeSet}

final class MethodCallTyping(
  private[typing] val typing: Typing,
  private val bodyContext: TypingBodyContext,
  private val body: TypingBodyPass
) {
  private val callArgumentTypingSupport = new CallArgumentTypingSupport(bodyContext, typed(_, _, _), processAssignable)
  private val methodInvocationBuilderSupport = new MethodInvocationBuilderSupport(typing, this)
  private val methodCallReportingSupport = new MethodCallReportingSupport(bodyContext, this)
  private val methodTargetTypingSupport = new MethodTargetTypingSupport(bodyContext)
  private val memberSelectionResolutionSupport = new MemberSelectionResolutionSupport(typing, bodyContext, this)
  private val memberSelectionTypingSupport = new MemberSelectionTypingSupport(bodyContext, this)
  private val methodCallFallbackSupport = new MethodCallFallbackSupport(typing, this)
  private val instanceMethodCallSupport = new InstanceMethodCallSupport(bodyContext, this, methodCallFallbackSupport)
  private val safeNavigationTypingSupport = new SafeNavigationTypingSupport(bodyContext, this)
  private val staticMethodCallSupport = new StaticMethodCallSupport(typing, this)
  private val unqualifiedMethodCallSupport = new UnqualifiedMethodCallSupport(bodyContext, this, methodCallFallbackSupport, staticMethodCallSupport)
  private val superMethodCallSupport = new SuperMethodCallSupport(bodyContext, this)
  private val argumentRetyping = new ArgumentExpectedTypeRetyping(typing, this)

  /**
   * Re-type "malleable" arguments (a generic static/unqualified call or a
   * collection literal) against a uniquely-determined candidate's parameter
   * types, so a generic call in argument position pins its type arguments from
   * the expected type (issue #232). Returns updated params only on a change.
   */
  private[typing] def retypeArgumentsForExpected(
    sourceType: ObjectType,
    name: String,
    args: Seq[AST.Expression],
    params: Array[Term],
    context: LocalContext,
    filter: Method => Boolean,
    receiverType: Type = null
  ): Option[Array[Term]] =
    argumentRetyping.retypeArguments(sourceType, name, args, params, context, filter, receiverType)

  /** Select a single method, reporting errors if none found or ambiguous */
  private[typing] def selectSingleMethod(
    node: AST.Node,
    targetType: ObjectType,
    name: String,
    methods: Array[Method],
    argTypes: Array[Type]
  ): Option[Method] = methods match {
    case Array() =>
      reportMethodNotFound(node, targetType, name, argTypes)
      None
    case Array(m) =>
      if (!MemberAccess.isMemberAccessible(m, bodyContext.definition)) {
        bodyContext.report(SemanticError.METHOD_NOT_ACCESSIBLE, node, targetType, name, argTypes, bodyContext.definition)
        None
      } else Some(m)
    case _ =>
      reportAmbiguousMethods(node, name, methods)
      None
  }

  /** Resolve method type arguments - explicit if provided, otherwise inferred */
  private[typing] def resolveMethodTypeArgs(
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

  private[typing] def resolveInvocation(
    node: AST.Node,
    method: Method,
    params: Array[Term],
    typeArgs: List[AST.TypeNode],
    classSubst: scala.collection.immutable.Map[String, Type],
    expected: Type
  ): Option[ResolvedMethodInvocation] =
    methodInvocationBuilderSupport.resolveInvocation(node, method, params, typeArgs, classSubst, expected)

  /** Collects methods matching the filter from a type hierarchy into candidates set */
  private[typing] def collectMethodsMatching(
    sourceType: ObjectType,
    name: String,
    candidates: JTreeSet[Method],
    filter: Method => Boolean
  ): Unit = {
    def collect(currentType: ObjectType): Unit = {
      if (currentType == null) return
      currentType.methods(name).foreach { method =>
        if (filter(method)) candidates.add(method)
      }
      collect(currentType.superClass)
      currentType.interfaces.foreach(collect)
    }
    collect(sourceType)
  }

  /** Filter for instance (non-static) methods */
  private[typing] def isInstanceMethod(m: Method): Boolean = (m.modifier & AST.M_STATIC) == 0

  /** Filter for static methods */
  private[typing] def isStaticMethod(m: Method): Boolean = (m.modifier & AST.M_STATIC) != 0

  /** Information extracted from named arguments */
  // Named argument helpers delegated to ArgumentHelpers object

  /** Process parameters with type checking, returns None on error */
  private[typing] def processParamsWithExpected(
    node: AST.Node,
    params: Array[Term],
    expectedArgs: Array[Type]
  ): Option[Array[Term]] =
    callArgumentTypingSupport.processParamsWithExpected(node, params, expectedArgs)

  /** Process parameters with args and expected types, returns None on error */
  private[typing] def processParamsWithArgs(
    args: Seq[AST.Expression],
    params: Array[Term],
    expectedArgs: Array[Type]
  ): Option[Array[Term]] =
    callArgumentTypingSupport.processParamsWithArgs(args, params, expectedArgs)

  /**
   * Wrap parameters for vararg method call.
   * If the method is vararg, wraps trailing arguments into an array.
   * Returns the adjusted parameters array.
   */
  private[typing] def wrapVarargParams(
    method: Method,
    params: Array[Term],
    expectedArgs: Array[Type]
  ): Array[Term] =
    callArgumentTypingSupport.wrapVarargParams(method, params, expectedArgs)

  private[typing] def prepareCallParams(
    node: AST.Node,
    args: Seq[AST.Expression],
    method: Method,
    params: Array[Term],
    expectedArgs: Array[Type]
  ): Option[Array[Term]] =
    callArgumentTypingSupport.prepareCallParams(node, args, method, params, expectedArgs)

  private[typing] def isAssignableWithBoxing(target: Type, source: Type): Boolean = {
    TypeRelations.isAssignableWithBoxing(target, source, typing.table_)
  }

  private[typing] def buildResolvedCall(
    node: AST.Node,
    method: Method,
    params: Array[Term],
    typeArgs: List[AST.TypeNode],
    classSubst: scala.collection.immutable.Map[String, Type],
    expected: Type,
    context: LocalContext = null
  )(
    prepareParams: Array[Type] => Option[Array[Term]],
    buildRawCall: Array[Term] => Term
  ): Option[Term] =
    methodInvocationBuilderSupport.buildResolvedCall(node, method, params, typeArgs, classSubst, expected, context)(prepareParams, buildRawCall)

  private[typing] def castCall(
    call: Term,
    method: Method,
    classSubst: scala.collection.immutable.Map[String, Type],
    methodSubst: scala.collection.immutable.Map[String, Type]
  ): Term =
    methodInvocationBuilderSupport.castCall(call, method, classSubst, methodSubst)

  private[typing] def normalizeMethodCallTarget(
    node: AST.MethodCall,
    target: Term
  ): Option[ResolvedMethodTarget] =
    methodTargetTypingSupport.normalizeMethodCallTarget(node, target)

  private[typing] def normalizeSafeMethodCallTarget(
    node: AST.SafeMethodCall,
    target: Term
  ): Option[ResolvedMethodTarget] =
    methodTargetTypingSupport.normalizeSafeMethodCallTarget(node, target)

  private[typing] def normalizeMemberSelectionTarget(
    node: AST.MemberSelection,
    target: Term
  ): Option[ResolvedMemberSelectionTarget] =
    memberSelectionResolutionSupport.normalizeMemberSelectionTarget(node, target)

  private[typing] def normalizeSafeMemberSelectionTarget(
    node: AST.SafeMemberSelection,
    target: Term
  ): Option[ResolvedMemberSelectionTarget] =
    memberSelectionResolutionSupport.normalizeSafeMemberSelectionTarget(node, target)

  private[typing] def resolveMemberSelection(
    node: AST.Node,
    targetType: ObjectType,
    name: String
  ): Option[ResolvedMemberSelection] =
    memberSelectionResolutionSupport.resolveMemberSelection(node, targetType, name)

  private[typing] def reportMethodNotFound(
    node: AST.Node,
    targetType: AnyRef,
    name: String,
    argTypes: Array[Type]
  ): Unit =
    methodCallReportingSupport.reportMethodNotFound(node, targetType, name, argTypes)

  private[typing] def reportAmbiguousMethods(
    node: AST.Node,
    name: String,
    methods: Array[Method]
  ): Unit =
    methodCallReportingSupport.reportAmbiguousMethods(node, name, methods)

  private[typing] def reportAmbiguousMethod(
    node: AST.Node,
    first: Method,
    second: Method,
    name: String = null
  ): Unit =
    methodCallReportingSupport.reportAmbiguousMethod(node, first, second, name)

  private[typing] def reportAmbiguousSignature(
    node: AST.Node,
    firstAffiliation: AnyRef,
    firstName: String,
    firstArguments: Array[Type],
    secondAffiliation: AnyRef,
    secondName: String,
    secondArguments: Array[Type]
  ): Unit =
    methodCallReportingSupport.reportAmbiguousSignature(
      node,
      firstAffiliation,
      firstName,
      firstArguments,
      secondAffiliation,
      secondName,
      secondArguments
    )

  private[typing] def reportIllegalMethodCall(
    node: AST.Node,
    method: Method,
    name: String
  ): Unit =
    methodCallReportingSupport.reportIllegalMethodCall(node, method, name)

  def typeMemberSelection(node: AST.MemberSelection, context: LocalContext): Option[Term] =
    memberSelectionTypingSupport.typeMemberSelection(node, context)

  def typeMethodCall(node: AST.MethodCall, context: LocalContext, expected: Type = null): Option[Term] =
    instanceMethodCallSupport.typeMethodCall(node, context, expected)

  private[typing] def typeMethodCallOnObject(node: AST.MethodCall, target: Term, targetType: ObjectType, params: Array[Term], context: LocalContext, expected: Type = null): Option[Term] =
    instanceMethodCallSupport.typeMethodCallOnObject(node, target, targetType, params, context, expected)

  def typeUnqualifiedMethodCall(node: AST.UnqualifiedMethodCall, context: LocalContext, expected: Type = null): Option[Term] =
    unqualifiedMethodCallSupport.typeUnqualifiedMethodCall(node, context, expected)

  private[typing] def mapTypeArgs(typeArgs: List[AST.TypeNode]): Option[Array[Type]] =
    typeArgs.foldLeft(Option(List.empty[Type])) { (accOpt, typeArg) =>
      accOpt.flatMap { acc =>
        typing.mapFrom(typeArg).flatMap { mapped =>
          if (mapped eq BasicType.VOID) { typing.report(TYPE_ARGUMENT_MUST_BE_REFERENCE, typeArg, mapped.name); None }
          else Some(acc :+ mapped)
        }
      }
    }.map(_.toArray)

  def typeStaticMemberSelection(node: AST.StaticMemberSelection): Option[Term] =
    staticMethodCallSupport.typeStaticMemberSelection(node)

  def typeStaticMethodCall(node: AST.StaticMethodCall, context: LocalContext, expected: Type = null): Option[Term] =
    staticMethodCallSupport.typeStaticMethodCall(node, context, expected)

  def typeSuperMethodCall(node: AST.SuperMethodCall, context: LocalContext, expected: Type = null): Option[Term] =
    superMethodCallSupport.typeSuperMethodCall(node, context, expected)

  private[typing] def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)

  private[typing] def typedTerms(nodes: Array[AST.Expression], context: LocalContext): Array[Term] =
    body.typedTerms(nodes, context)

  private[typing] def closureMatchesSam(node: AST.ClosureExpression, context: LocalContext, target: Type): Option[Boolean] =
    body.closureMatchesSam(node, context, target)

  private[typing] def processAssignable(node: AST.Node, expected: Type, term: Term): Term =
    body.processAssignable(node, expected, term)

  private[typing] def tryFindMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Either[Boolean, Method] =
    body.tryFindMethod(node, target, name, params)

  /** Resolve an operator convention method via an `extension` block; None on miss. */
  private[typing] def tryExtensionOperatorMethod(
    node: AST.Node,
    name: String,
    target: Term,
    targetType: ObjectType,
    param: Term,
    expected: Type
  ): Option[Term] =
    methodCallFallbackSupport.tryExtensionOperatorMethod(node, name, target, targetType, param, expected)

  private[typing] def types(terms: Array[Term]): Array[Type] =
    body.types(terms)

  private[typing] def typeNames(types: Array[Type]): Array[String] =
    body.typeNames(types)

  private[typing] def getter(name: String): String =
    MethodNames.GET_PREFIX + Character.toUpperCase(name.charAt(0)) + name.substring(1)

  private[typing] def getterBoolean(name: String): String =
    MethodNames.IS_PREFIX + Character.toUpperCase(name.charAt(0)) + name.substring(1)

  /**
   * 名前付き引数を含む引数リストを並べ替えて型付けする
   * @return 成功時は Some(並べ替えられた引数配列), エラー時は None
   */
  private[typing] def processNamedArguments(
    node: AST.Node,
    args: List[AST.Expression],
    method: Method,
    context: LocalContext
  ): Option[Array[Term]] =
    callArgumentTypingSupport.processNamedArguments(node, args, method, context)

  // hasNamedArguments delegated to ArgumentHelpers object

  /**
   * Type a safe member selection: expr?.name
   * Returns null if target is null, otherwise accesses the field.
   */
  def typeSafeMemberSelection(node: AST.SafeMemberSelection, context: LocalContext): Option[Term] =
    safeNavigationTypingSupport.typeSafeMemberSelection(node, context)

  /**
   * Type a safe method call: expr?.method(args)
   * Returns null if target is null, otherwise calls the method.
   */
  def typeSafeMethodCall(node: AST.SafeMethodCall, context: LocalContext, expected: Type = null): Option[Term] =
    safeNavigationTypingSupport.typeSafeMethodCall(node, context, expected)
}
