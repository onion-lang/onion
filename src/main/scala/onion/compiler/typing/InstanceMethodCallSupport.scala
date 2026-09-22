package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

import java.util.{TreeSet => JTreeSet}

import scala.jdk.CollectionConverters.*

import ArgumentHelpers.{hasNamedArguments, untypedClosureIndicesOf}

private[compiler] final class InstanceMethodCallSupport(
  bodyContext: TypingBodyContext,
  calls: MethodCallTyping,
  fallback: MethodCallFallbackSupport
) {
  private val overloadSupport = new CallOverloadSupport(calls.typing, calls)

  def typeMethodCall(node: AST.MethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    val target = calls.typed(node.target, context).getOrElse(null)
    if (target == null) return None

    val untypedClosureIndices = untypedClosureIndicesOf(node.args)

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
    params0: Array[Term],
    context: LocalContext,
    expected: Type = null
  ): Option[Term] = {
    val name = node.name

    if (hasNamedArguments(node.args)) {
      return typeMethodCallWithNamedArgs(node, target, targetType, context, expected)
    }

    // Zero-arg record copy is a full clone: p.copy()
    if (name == "copy" && node.args.isEmpty) {
      calls.tryRecordCopy(node, name, node.args, node.typeArgs, context, target, targetType) {
        (method, finalParams) => new Call(target, method, finalParams)
      } match {
        case some @ Some(_) => return some
        case None =>
      }
    }

    if (params0 == null) {
      val untypedClosureIndices = untypedClosureIndicesOf(node.args)
      return fallback.typeMethodCallWithBidirectionalInference(node, target, targetType, context, expected, untypedClosureIndices)
    }

    val methods0 = MethodResolution.findMethods(targetType, name, params0, bodyContext.table)

    // When no method applies, an argument that is a generic call (e.g.
    // `Result::ok(11)`) may have left its type parameters unbound (-> Object).
    // Re-type such arguments against the single candidate's parameter types so
    // the expected type pins them, then retry resolution (issue #232).
    val (params, methods) =
      if (methods0.length == 0)
        calls.retypeArgumentsForExpected(targetType, name, node.args, params0, context, calls.isInstanceMethod, target.`type`) match {
          case Some(newParams) =>
            val remethods = MethodResolution.findMethods(targetType, name, newParams, bodyContext.table)
            if (remethods.length > 0) (newParams, remethods) else (params0, methods0)
          case None => (params0, methods0)
        }
      else (params0, methods0)

    if (methods.length == 0) {
      val closureIndices = node.args.zipWithIndex.collect {
        case (expr, i) if expr.isInstanceOf[AST.ClosureExpression] => i
      }.toSet
      if (closureIndices.nonEmpty) {
        // For a primitive receiver (e.g. Int boxed to Integer), native methods
        // cannot exist on the boxed type, so an extension is the only possible
        // match. Try it before re-entering bidirectional inference so that a
        // typed closure (`{ (n: Int) -> ... }`) reaches the extension lookup
        // rather than disappearing into the bidirectional re-inference path.
        // For reference receivers (e.g. List), bidirectional inference is
        // left in charge: it resolves overloads like Colls.map vs Iterables.map
        // that would incorrectly surface as ambiguous if tried with typed params.
        if (target.`type`.isBasicType) {
          val ext = fallback.tryExtensionMethodCall(node, target, targetType, params, expected, reportIfNotFound = false)
          if (ext.isDefined) return ext
        }
        return fallback.typeMethodCallWithBidirectionalInference(node, target, targetType, context, expected, closureIndices)
      }
      // Resolution order for an unmatched call: extension methods first (a
      // user-declared extension wins), then a bean-property getter as a last
      // resort. The getter fallback lets `e.message()` resolve to getMessage() like
      // `e.message` does, so parens are optional on property accessors too.
      val ext = fallback.tryExtensionMethodCall(node, target, targetType, params, expected, reportIfNotFound = false)
      if (ext.isDefined) return ext
      if (params != null && params.length == 0) {
        val getters = {
          val g = MethodResolution.findMethods(targetType, calls.getter(name), params, bodyContext.table)
          if (g.length > 0) g else MethodResolution.findMethods(targetType, calls.getterBoolean(name), params, bodyContext.table)
        }
        if (getters.length > 0) {
          calls.selectSingleMethod(node, targetType, name, getters, calls.types(params)) match {
            case Some(getter) if (getter.modifier & AST.M_STATIC) == 0 =>
              val classSubst = TypeSubstitution.hierarchySubstitution(target.`type`, getter.affiliation)
              return calls.buildResolvedCall(node, getter, params, node.typeArgs, classSubst, expected)(
                expectedArgs => calls.prepareCallParams(node, node.args, getter, params, expectedArgs),
                finalParams => new Call(target, getter, finalParams)
              )
            case _ => // no usable getter
          }
        }
      }
      // Nothing matched: report not-found (extension fallback was silent above).
      calls.reportMethodNotFound(node, targetType, name, calls.types(params))
      return None
    }

    calls.selectSingleMethod(node, targetType, name, methods, calls.types(params)) match {
      case None => None
      case Some(method) if (method.modifier & AST.M_STATIC) != 0 =>
        calls.reportIllegalMethodCall(node, method, name)
        None
      case Some(method) =>
        val classSubst = TypeSubstitution.hierarchySubstitution(target.`type`, method.affiliation)
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
    val recordCopy = calls.tryRecordCopy(node, node.name, node.args, node.typeArgs, context, target, targetType) {
      (method, finalParams) => new Call(target, method, finalParams)
    }
    if (recordCopy.isDefined) return recordCopy

    val candidates = new JTreeSet[Method](new MethodComparator)
    calls.collectMethodsMatching(targetType, node.name, candidates, calls.isInstanceMethod)
    if (candidates.isEmpty) {
      calls.reportMethodNotFound(node, targetType, node.name, Array[Type]())
      return None
    }

    overloadSupport.selectNamedArgumentMethod(candidates, node.args) match {
      case CandidateSelection.NoMatch =>
        ArgumentHelpers.findUnknownNamedArg(candidates.asScala, node.args) match {
          case Some(named) =>
            bodyContext.report(UNKNOWN_PARAMETER_NAME, named, named.name,
              ArgumentHelpers.parameterNameCandidates(candidates.asScala))
          case None => calls.reportMethodNotFound(node, targetType, node.name, Array[Type]())
        }
        None
      case CandidateSelection.Ambiguous(first, second) =>
        calls.reportAmbiguousMethod(node, first, second, node.name)
        None
      case CandidateSelection.Selected(method) =>
        val classSubst = TypeSubstitution.hierarchySubstitution(target.`type`, method.affiliation)
        calls.processNamedArguments(node, node.args, method, context).flatMap { params =>
          calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected)(
            expectedArgs => calls.processParamsWithExpected(node, params, expectedArgs),
            finalParams => new Call(target, method, finalParams)
          )
        }
    }
  }

}
