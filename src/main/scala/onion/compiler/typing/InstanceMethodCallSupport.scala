package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

import java.util.{TreeSet => JTreeSet}

import ArgumentHelpers.hasNamedArguments

private[compiler] final class InstanceMethodCallSupport(
  bodyContext: TypingBodyContext,
  calls: MethodCallTyping,
  fallback: MethodCallFallbackSupport
) {
  private val overloadSupport = new CallOverloadSupport(calls.typing, calls)

  def typeMethodCall(node: AST.MethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    val target = calls.typed(node.target, context).getOrElse(null)
    if (target == null) return None

    val untypedClosureIndices = node.args.zipWithIndex.collect {
      case (expr, i) if isClosureWithUntypedParams(expr) => i
    }.toSet

    val params =
      if (untypedClosureIndices.isEmpty) calls.typedTerms(node.args.toArray, context)
      else null

    if (params == null && untypedClosureIndices.isEmpty) return None

    calls.normalizeMethodCallTarget(node, target).flatMap { resolved =>
      typeMethodCallOnObject(node, resolved.term, resolved.targetType, params, context, expected)
    }
  }

  def typeMethodCallOnObject(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    params: Array[Term],
    context: LocalContext,
    expected: Type = null
  ): Option[Term] = {
    val name = node.name

    if (hasNamedArguments(node.args)) {
      return typeMethodCallWithNamedArgs(node, target, targetType, context, expected)
    }

    if (params == null) {
      val untypedClosureIndices = node.args.zipWithIndex.collect {
        case (expr, i) if isClosureWithUntypedParams(expr) => i
      }.toSet
      return fallback.typeMethodCallWithBidirectionalInference(node, target, targetType, context, expected, untypedClosureIndices)
    }

    val methods = MethodResolution.findMethods(targetType, name, params, bodyContext.table)
    if (methods.length == 0) {
      return fallback.tryExtensionMethodCall(node, target, targetType, params, expected)
    }

    calls.selectSingleMethod(node, targetType, name, methods, calls.types(params)) match {
      case None => None
      case Some(method) if (method.modifier & AST.M_STATIC) != 0 =>
        calls.reportIllegalMethodCall(node, method, name)
        None
      case Some(method) =>
        val classSubst = TypeSubstitution.classSubstitution(target.`type`)
        calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected)(
          expectedArgs => calls.prepareCallParams(node, node.args, method, params, expectedArgs),
          finalParams => new Call(target, method, finalParams)
        )
    }
  }

  private def typeMethodCallWithNamedArgs(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    context: LocalContext,
    expected: Type
  ): Option[Term] = {
    val candidates = new JTreeSet[Method](new MethodComparator)
    calls.collectMethodsMatching(targetType, node.name, candidates, calls.isInstanceMethod)
    if (candidates.isEmpty) {
      calls.reportMethodNotFound(node, targetType, node.name, Array[Type]())
      return None
    }

    overloadSupport.selectNamedArgumentMethod(candidates, node.args) match {
      case CandidateSelection.NoMatch =>
        calls.reportMethodNotFound(node, targetType, node.name, Array[Type]())
        None
      case CandidateSelection.Ambiguous(first, second) =>
        calls.reportAmbiguousMethod(node, first, second, node.name)
        None
      case CandidateSelection.Selected(method) =>
        val classSubst = TypeSubstitution.classSubstitution(target.`type`)
        calls.processNamedArguments(node, node.args, method, context).flatMap { params =>
          calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected)(
            expectedArgs => calls.processParamsWithExpected(node, params, expectedArgs),
            finalParams => new Call(target, method, finalParams)
          )
        }
    }
  }

  private def hasUntypedParams(closure: AST.ClosureExpression): Boolean =
    closure.args.exists(_.typeRef == null)

  private def isClosureWithUntypedParams(expr: AST.Expression): Boolean =
    expr match {
      case closure: AST.ClosureExpression => hasUntypedParams(closure)
      case _ => false
    }
}
