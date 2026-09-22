package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

import java.util.{TreeSet => JTreeSet}

import scala.jdk.CollectionConverters.*

import ArgumentHelpers.hasNamedArguments

private[compiler] final class SafeNavigationTypingSupport(
  bodyContext: TypingBodyContext,
  calls: MethodCallTyping
) {
  private val overloadSupport = new CallOverloadSupport(calls.typing, calls)

  def typeSafeMemberSelection(node: AST.SafeMemberSelection, context: LocalContext): Option[Term] = {
    val target = calls.typed(node.target, context).getOrElse(null)
    if (target == null) return None

    calls.normalizeSafeMemberSelectionTarget(node, target).flatMap { resolved =>
      def buildResolved(r: ResolvedMemberSelection): Term = r match {
        case ResolvedArrayLengthSelection =>
          val lengthField = new FieldDefinition(node.location, 0, null, "length", BasicType.INT)
          new SafeFieldAccess(node.location, resolved.term, lengthField)
        case ResolvedFieldSelection(field) =>
          new SafeFieldAccess(node.location, resolved.term, field)
        case ResolvedGetterSelection(method) =>
          new SafeCall(node.location, resolved.term, method, Array.empty)
      }
      // Resolution order mirrors the plain (non-null-safe) member-selection path
      // (MemberSelectionTypingSupport): (1) regular field/getter lookup; (2)
      // zero-arg extension method (property-style `def name: T`); (3) report error.
      calls.resolveMemberSelection(node, resolved.targetType, node.name, reportErrorIfMissing = false) match {
        case Some(r) => Some(buildResolved(r))
        case None =>
          calls.tryZeroArgExtensionAccessForSafeNav(node, node.name, resolved.term, resolved.targetType, null).orElse {
            calls.resolveMemberSelection(node, resolved.targetType, node.name, reportErrorIfMissing = true)
              .map(buildResolved)
          }
      }
    }
  }

  def typeSafeMethodCall(node: AST.SafeMethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    val target = calls.typed(node.target, context).getOrElse(null)
    if (target == null) return None

    val params = calls.typedTerms(node.args.toArray, context)
    if (params == null) return None

    calls.normalizeSafeMethodCallTarget(node, target).flatMap { resolved =>
      typeSafeMethodCallOnObject(node, resolved.term, resolved.targetType, params, context, expected)
    }
  }

  private def typeSafeMethodCallOnObject(
    node: AST.SafeMethodCall,
    target: Term,
    targetType: ObjectType,
    params: Array[Term],
    context: LocalContext,
    expected: Type
  ): Option[Term] = {
    val name = node.name

    if (hasNamedArguments(node.args)) {
      return typeSafeMethodCallWithNamedArgs(node, target, targetType, context, expected)
    }

    // Zero-arg record copy is a full clone: p?.copy()
    if (name == "copy" && node.args.isEmpty) {
      calls.tryRecordCopy(node, name, node.args, node.typeArgs, context, target, targetType) {
        (method, finalParams) => new SafeCall(node.location, target, method, finalParams)
      } match {
        case some @ Some(_) => return some
        case None =>
      }
    }

    val methods = MethodResolution.findMethods(targetType, name, params, bodyContext.table)
    if (methods.length == 0) {
      // No instance method found; fall back to extension methods before reporting an error
      return calls.tryExtensionMethodCallForSafeNav(node, target, targetType, params, expected)
    }

    calls.selectSingleMethod(node, targetType, name, methods, calls.types(params)) match {
      case None => None
      case Some(method) if (method.modifier & AST.M_STATIC) != 0 =>
        calls.reportIllegalMethodCall(node, method, name)
        None
      case Some(method) =>
        val classSubst = TypeSubstitution.hierarchySubstitution(targetType, method.affiliation)
        calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected)(
          expectedArgs => calls.prepareCallParams(node, node.args, method, params, expectedArgs),
          finalParams => new SafeCall(node.location, target, method, finalParams)
        )
    }
  }

  /**
   * Named-argument safe method call: obj?.method(b = 1, a = 2). Mirrors
   * InstanceMethodCallSupport.typeMethodCallWithNamedArgs for the plain
   * (non-null-safe) path, wrapping the resolved call as a SafeCall.
   */
  private def typeSafeMethodCallWithNamedArgs(
    node: AST.SafeMethodCall,
    target: Term,
    targetType: ObjectType,
    context: LocalContext,
    expected: Type
  ): Option[Term] = {
    val recordCopy = calls.tryRecordCopy(node, node.name, node.args, node.typeArgs, context, target, targetType) {
      (method, finalParams) => new SafeCall(node.location, target, method, finalParams)
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
        val classSubst = TypeSubstitution.hierarchySubstitution(targetType, method.affiliation)
        calls.processNamedArguments(node, node.args, method, context).flatMap { params =>
          calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected)(
            expectedArgs => calls.processParamsWithExpected(node, params, expectedArgs),
            finalParams => new SafeCall(node.location, target, method, finalParams)
          )
        }
    }
  }
}
