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

    // A named closure is typed against its parameter by the named-argument path (#2799),
    // so it is not typed here with no expected type.
    val namedClosure = node.args.exists {
      case named: AST.NamedArgument => ArgumentHelpers.isClosureWithUntypedParams(named.value)
      case _ => false
    }
    val deferred = untypedClosureIndices.nonEmpty || namedClosure

    val params =
      if (!deferred) calls.typedTerms(node.args.toArray, context)
      else null

    if (params == null && !deferred) return None

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
  ): Option[Term] =
    // An extension block declaring two `name`s with one erasure is already E0084; a call to
    // that name, whichever twin it means, reports nothing of its own (#2475).
    if (fallback.hasErasureTwinExtension(targetType, node.name, target)) {
      // Only the call's own resolution errors (E0000/E0005/E0006 at the call) are redundant;
      // an error inside a lambda argument is real and stays (#2550).
      val start = calls.typing.problemCount
      val result = typeMethodCallOnObjectChecked(node, target, targetType, params0, context, expected)
      val callLocation = node.location
      // An argument's start, and a typed lambda's parameters: the twin the call did not mean
      // reports its mismatch there.
      val argLocations: Set[Location] = node.args.flatMap {
        case c: AST.ClosureExpression =>
          c.location :: c.args.flatMap(a => List(a.location) ++ Option(a.typeRef).map(_.location))
        case a => List(a.location)
      }.toSet
      calls.typing.reporter_.dropProblemsSince(start) { e =>
        e.errorCode.exists {
          case "E0005" => e.location == callLocation
          case "E0000" | "E0006" => e.location == callLocation || argLocations.contains(e.location)
          case _ => false
        }
      }
      result
    } else typeMethodCallOnObjectChecked(node, target, targetType, params0, context, expected)

  private def typeMethodCallOnObjectChecked(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    params0: Array[Term],
    context: LocalContext,
    expected: Type
  ): Option[Term] = {
    val name = node.name

    if (hasNamedArguments(node.args)) {
      return typeMethodCallWithNamedArgs(node, target, targetType, context, expected)
    }

    // Zero-arg record copy is a full clone: p.copy()
    if (name == "copy" && node.args.isEmpty) {
      calls.tryRecordCopy(node, name, node.args, node.typeArgs, context, target, targetType) {
        (receiver, method, finalParams) => new Call(receiver, method, finalParams)
      } match {
        case Some(result) => return result
        case None =>
      }
    }

    if (params0 == null) {
      val untypedClosureIndices = untypedClosureIndicesOf(node.args)
      return fallback.typeMethodCallWithBidirectionalInference(node, target, targetType, context, expected, untypedClosureIndices)
    }

    val methods00 = MethodResolution.findMethods(targetType, name, params0, bodyContext.table)
    // The most specific overload can be one the call may not name (`sb.append(sb2)` finds
    // AbstractStringBuilder's package-private append(AbstractStringBuilder)). Java resolves
    // among accessible members only, so retry without the inaccessible ones (#2818); when
    // nothing else applies the E0013 below stays.
    val methods0 =
      if (methods00.length == 1 && !MemberAccess.isMemberAccessible(methods00(0), bodyContext.definition)) {
        val accessible = MethodResolution.findMethods(targetType, name, params0, bodyContext.table,
          m => MemberAccess.isMemberAccessible(m, bodyContext.definition))
        if (accessible.length > 0) accessible else methods00
      } else methods00

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
      else if (methods0.length == 1 && calls.hasMalleableArgs(node.args))
        // `f(List[T])` / `f(Object)` with `Collections::emptyList()`: the factory's element type is
        // unpinned, so only `f(Object)` applies. Java pins it from the slot and takes `f(List)` (#2841).
        calls.retypeArgumentsForOverloads(targetType, name, node.args, params0, context, calls.isInstanceMethod, target.`type`,
          (ps, c) => { val r = MethodResolution.findMethods(targetType, name, ps, bodyContext.table); r.length == 1 && (r(0) eq c) && (c ne methods0(0)) }) match {
          case Some(newParams) => (newParams, MethodResolution.findMethods(targetType, name, newParams, bodyContext.table))
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
        // Every lambda here declares its parameter types (params are typed), so a user
        // extension (one, or several overloads) can take them as they are; re-typing the
        // bodies per candidate made nested calls exponential (#2463).
        val userExt = fallback.tryUserExtensionWithTypedArguments(node, target, targetType, params, expected)
        if (userExt.isDefined) return userExt.get
        return fallback.typeMethodCallWithBidirectionalInference(node, target, targetType, context, expected, closureIndices)
      }
      // Resolution order for an unmatched call: extension methods first (a
      // user-declared extension wins), then a bean-property getter as a last
      // resort. The getter fallback lets `e.message()` resolve to getMessage() like
      // `e.message` does, so parens are optional on property accessors too.
      val ext = fallback.tryExtensionMethodCall(node, target, targetType, params, expected, reportIfNotFound = false)
      if (ext.isDefined) return ext
      if (fallback.isAmbiguousExtensionCall(targetType, name, params, target)) return None
      if (params != null && params.length == 0) {
        val getters = {
          val g = MethodResolution.findMethods(targetType, calls.getter(name), params, bodyContext.table)
          if (g.length > 0) g else MethodResolution.findMethods(targetType, calls.getterBoolean(name), params, bodyContext.table)
        }
        if (getters.length > 0) {
          calls.selectSingleMethod(node, targetType, name, getters, calls.types(params)) match {
            case Some(getter) if (getter.modifier & AST.M_STATIC) == 0 =>
              val classSubst = TypeSubstitution.hierarchySubstitution(target.`type`, getter.affiliation, TypeSubstitution.isSelfReceiver(target))
              return calls.buildResolvedCall(node, getter, params, node.typeArgs, classSubst, expected, context)(
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
        val classSubst = TypeSubstitution.hierarchySubstitution(target.`type`, method.affiliation, TypeSubstitution.isSelfReceiver(target))
        // `() -> Option::none()` takes its result type from the target type (#3067).
        val targetTyped =
          if (node.typeArgs.nonEmpty) params
          else calls.retypeTargetTypedClosures(node, method, node.args, params, classSubst, context, expected)
        calls.buildResolvedCall(node, method, targetTyped, node.typeArgs, classSubst, expected, context)(
          expectedArgs => calls.prepareCallParams(node, node.args, method, targetTyped, expectedArgs),
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
      (receiver, method, finalParams) => new Call(receiver, method, finalParams)
    }
    recordCopy match { case Some(result) => return result; case None => }

    val candidates = new JTreeSet[Method](new MethodComparator)
    calls.collectMethodsMatching(targetType, node.name, candidates, calls.isInstanceMethod)
    if (candidates.isEmpty) {
      calls.reportMethodNotFound(node, targetType, node.name, Array[Type]())
      return None
    }

    val pretyped = new java.util.IdentityHashMap[AST.Expression, Term]()
    val selection = overloadSupport.selectNamedArgumentMethod(candidates, node.args, context, m => TypeSubstitution.hierarchySubstitution(target.`type`, m.affiliation, TypeSubstitution.isSelfReceiver(target)), pretyped) match {
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
        // The positional path reports E0013 here; this one compiled a private call that
        // threw IllegalAccessError at run time (#2916).
        bodyContext.report(METHOD_NOT_ACCESSIBLE, node, method.affiliation, node.name, method.arguments, bodyContext.definition)
        None
      case CandidateSelection.Selected(method) =>
        val classSubst = TypeSubstitution.hierarchySubstitution(target.`type`, method.affiliation, TypeSubstitution.isSelfReceiver(target))
        calls.processNamedArguments(node, node.args, method, context, pretyped).flatMap { params =>
          calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected, context)(
            expectedArgs => calls.processParamsWithExpected(node, params, expectedArgs, method)
              .map(ArgumentHelpers.evaluateInWrittenOrder(node.args, method.argumentsWithDefaults.toSeq.map(_.name), _, context)),
            finalParams => new Call(target, method, finalParams)
          )
        }
    }
  }

}
