package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

import java.util.{TreeSet => JTreeSet}

import scala.jdk.CollectionConverters.*

import ArgumentHelpers.{hasNamedArguments, untypedClosureIndicesOf}

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

    // An untyped lambda (`xs?.map { x -> x * 2 }`) takes its parameter types from the method,
    // so it cannot be typed before the method is chosen (#3007).
    if (untypedClosureIndicesOf(node.args).nonEmpty && !hasNamedArguments(node.args)) {
      typeSafeMethodCallWithUntypedLambdas(node, target, context, expected) match {
        case Some(result) => return result
        case None =>
      }
    }

    val params = calls.typedTerms(node.args.toArray, context)
    if (params == null) return None

    calls.normalizeSafeMethodCallTarget(node, target).flatMap { resolved =>
      typeSafeMethodCallOnObject(node, resolved.term, resolved.targetType, params, context, expected)
    }
  }

  /** The plain call each `x?.m(..)` with an untyped lambda is typed as, kept per node so that
   *  memoised overload decisions keyed by the call node are found again on a retyping. */
  private val plainCallOf = new java.util.IdentityHashMap[AST.SafeMethodCall, AST.MethodCall]()

  /**
   * `x?.m(.., y -> ..)`: typed as `x.m(..)` on a non-null view of `x`, through the same
   * bidirectional inference the `.` path uses, then rebuilt as a safe call on `x` itself.
   * The view is never emitted. Outer `None`: not handled here (a nullable primitive receiver),
   * so the eager path runs as before; `Some(None)`: handled, with its errors reported.
   */
  private def typeSafeMethodCallWithUntypedLambdas(
    node: AST.SafeMethodCall,
    target: Term,
    context: LocalContext,
    expected: Type
  ): Option[Option[Term]] = {
    val receiverType = target.`type` match {
      case n: NullableType => n.innerType
      case other => other
    }
    receiverType match {
      case objectType: ObjectType =>
        val view = new AsInstanceOf(node.target.location, target, objectType)
        val plain = plainCallOf.computeIfAbsent(node, n => AST.MethodCall(n.location, n.target, n.name, n.args, n.typeArgs))
        val plainExpected = expected match {
          case n: NullableType => n.innerType
          case other => other
        }
        Some(calls.typeMethodCallOnObject(plain, view, objectType, null, context, plainExpected).flatMap(asSafeCall(node, target, _)))
      case _ => None
    }
  }

  /** Rebuilds the plain call `typeSafeMethodCallWithUntypedLambdas` typed as a safe call on `target`. */
  private def asSafeCall(node: AST.SafeMethodCall, target: Term, plain: Term): Option[Term] = plain match {
    case cast: AsInstanceOf =>
      asSafeCall(node, target, cast.target).map { safe =>
        // As for the eager path: the result stays nullable, and a primitive is not unboxed.
        cast.destination match {
          case _: NullableType | _: NullType => TypeSubst.withCast(safe, cast.destination)
          case _: BasicType => safe
          case other => TypeSubst.withCast(safe, NullableType.of(other))
        }
      }
    case call: Call =>
      Some(new SafeCall(node.location, target, call.method, call.parameters))
    case call: CallStatic if call.parameters.nonEmpty =>
      // An extension method: its static's first parameter is the receiver, which codegen
      // takes from `target` after the null check.
      val receiverBasicType = call.method.arguments.headOption.collect { case bt: BasicType => bt }
      Some(new SafeStaticExtensionCall(node.location, target, call.target, call.method, receiverBasicType, call.parameters.tail))
    case _ =>
      // No other shape comes back from an instance call today; say so rather than miscompile.
      calls.reportMethodNotFound(node, target.`type`, node.name, Array[Type]())
      None
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
        (receiver, method, finalParams) => new SafeCall(node.location, receiver, method, finalParams)
      } match {
        case Some(result) => return result
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
        calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected, context)(
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
      (receiver, method, finalParams) => new SafeCall(node.location, receiver, method, finalParams)
    }
    recordCopy match { case Some(result) => return result; case None => }

    val candidates = new JTreeSet[Method](new MethodComparator)
    calls.collectMethodsMatching(targetType, node.name, candidates, calls.isInstanceMethod)
    if (candidates.isEmpty) {
      calls.reportMethodNotFound(node, targetType, node.name, Array[Type]())
      return None
    }

    val pretyped = new java.util.IdentityHashMap[AST.Expression, Term]()
    val selection = overloadSupport.selectNamedArgumentMethod(candidates, node.args, context, m => TypeSubstitution.hierarchySubstitution(targetType, m.affiliation), pretyped) match {
      case Some(found) => found
      case None => return None
    }
    selection match {
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
      case CandidateSelection.Selected(method)
          if !MemberAccess.isMemberAccessible(method, bodyContext.definition, target.`type`) =>
        // As in the plain named-argument path: E0013, not IllegalAccessError at run time (#2916).
        bodyContext.report(METHOD_NOT_ACCESSIBLE, node, method.affiliation, node.name, method.arguments, bodyContext.definition)
        None
      case CandidateSelection.Selected(method) =>
        val classSubst = TypeSubstitution.hierarchySubstitution(targetType, method.affiliation)
        calls.processNamedArguments(node, node.args, method, context, pretyped).flatMap { params =>
          calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected, context)(
            expectedArgs => calls.processParamsWithExpected(node, params, expectedArgs, method)
              .map(ArgumentHelpers.evaluateInWrittenOrder(node.args, method.argumentsWithDefaults.toSeq.map(_.name), _, context)),
            finalParams => new SafeCall(node.location, target, method, finalParams)
          )
        }
    }
  }
}
