package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

import java.util.{TreeSet => JTreeSet}

import scala.jdk.CollectionConverters.*

private[compiler] final class MethodCallFallbackSupport(
  typing: Typing,
  calls: MethodCallTyping
) {
  private val overloadSupport = new CallOverloadSupport(typing, calls)
  private val extensionMethodFallbackSupport = new ExtensionMethodFallbackSupport(typing, calls)

  def tryExtensionMethodCall(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    params: Array[Term],
    expected: Type,
    reportIfNotFound: Boolean = true
  ): Option[Term] =
    extensionMethodFallbackSupport.tryExtensionMethodCall(node, target, targetType, params, expected, reportIfNotFound)

  def typeMethodCallWithBidirectionalInference(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    context: LocalContext,
    expected: Type,
    untypedClosureIndices: Set[Int]
  ): Option[Term] =
    typeCallWithBidirectionalInferenceCore(
      node, node.name, node.args, node.typeArgs, target, targetType, context, expected, untypedClosureIndices
    )

  /**
   * Instance-method bidirectional inference over the raw call fields, decoupled
   * from the concrete AST node type. Shared by the qualified `obj.m(...)` path
   * (AST.MethodCall) and the unqualified `m(...)` path when it resolves to an
   * instance method on the current class (AST.UnqualifiedMethodCall), which
   * synthesizes a `This`/`OuterThis` target (issue #232).
   */
  def typeCallWithBidirectionalInferenceCore(
    node: AST.Node,
    name: String,
    argList: List[AST.Expression],
    typeArgs: List[AST.TypeNode],
    target: Term,
    targetType: ObjectType,
    context: LocalContext,
    expected: Type,
    untypedClosureIndices: Set[Int]
  ): Option[Term] = {
    val args = argList.toArray

    val preliminaryParams = new Array[Term](args.length)
    var hasNonClosureError = false

    for (i <- args.indices) {
      if (untypedClosureIndices.contains(i)) {
        preliminaryParams(i) = null
      } else {
        calls.typed(args(i), context) match {
          case Some(term) => preliminaryParams(i) = term
          case None =>
            hasNonClosureError = true
            preliminaryParams(i) = null
        }
      }
    }

    if (hasNonClosureError) return None

    // Extension-method fallback only applies to an explicit `obj.m(...)` call
    // (AST.MethodCall). A synthesized `this`/`OuterThis` target from the
    // unqualified path must not attempt it; when no instance method matches, its
    // caller resolves the remaining unqualified fallbacks (static-import,
    // callable-value, top-level static) instead.
    def extensionFallback(argTypes: Array[Type]): Option[Term] = node match {
      case mc: AST.MethodCall =>
        extensionMethodFallbackSupport.tryExtensionMethodCallBidirectional(
          mc, target, targetType, context, expected, untypedClosureIndices
        ) match {
          case some @ Some(_) => some
          case None =>
            calls.reportMethodNotFound(node, targetType, name, argTypes)
            None
        }
      case _ =>
        calls.reportMethodNotFound(node, targetType, name, argTypes)
        None
    }

    val candidates = new JTreeSet[Method](new MethodComparator)
    calls.collectMethodsMatching(targetType, name, candidates, calls.isInstanceMethod)

    if (candidates.isEmpty) {
      return extensionFallback(Array[Type]())
    }

    val nonClosureTypes = preliminaryParams.zipWithIndex.collect {
      case (term, i) if term != null => (i, term.`type`)
    }.toMap

    val applicableMethods = overloadSupport.collectPartialInstanceApplicables(
      target.`type`,
      candidates.asScala,
      node,
      preliminaryParams,
      expected
    )

    if (applicableMethods.isEmpty) {
      return extensionFallback(nonClosureTypes.values.toArray)
    }

    val disambiguated = overloadSupport.disambiguateClosureOverloads(
      applicableMethods,
      untypedClosureIndices,
      (i, samType) => args(i) match {
        case c: AST.ClosureExpression => calls.closureMatchesSam(c, context, samType)
        case _ => None
      }
    )

    val method = overloadSupport.selectMostSpecificApplicable(
      disambiguated,
      nonClosureTypes.keys.toSeq.sorted
    ) match {
      case CandidateSelection.Selected(selected) =>
        selected.method
      case CandidateSelection.Ambiguous(first, second) =>
        calls.reportAmbiguousMethod(node, first.method, second.method, name)
        return None
      case CandidateSelection.NoMatch =>
        calls.reportMethodNotFound(node, targetType, name, nonClosureTypes.values.toArray)
        return None
    }
    val classSubst = TypeSubstitution.hierarchySubstitution(target.`type`, method.affiliation)
    val nonClosureParams = preliminaryParams.filter(_ != null)
    val preliminaryMethodSubst = GenericMethodTypeArguments.inferWithoutDefaults(
      typing, node, method, nonClosureParams, classSubst, expected
    )
    val preliminaryExpectedArgs = method.arguments.map { argType =>
      TypeSubstitution.substituteType(argType, classSubst, preliminaryMethodSubst, defaultToBound = false)
    }

    val finalParams = new Array[Term](args.length)
    var hasError = false

    for (i <- args.indices) {
      if (untypedClosureIndices.contains(i)) {
        val expectedType = if (i < preliminaryExpectedArgs.length) preliminaryExpectedArgs(i) else null
        calls.typed(args(i), context, expectedType) match {
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

    if ((method.modifier & AST.M_STATIC) != 0) {
      calls.reportIllegalMethodCall(node, method, name)
      return None
    }

    val finalMethodSubst = GenericMethodTypeArguments.infer(typing, node, method, finalParams, classSubst, expected)
    val finalExpectedArgs = TypeSubst.args(method, classSubst, finalMethodSubst)

    for {
      finalProcessedParams <- calls.prepareCallParams(node, argList, method, finalParams, finalExpectedArgs)
    } yield {
      val call = new Call(target, method, finalProcessedParams)
      val castType = TypeSubst.result(method.returnType, classSubst, finalMethodSubst)
      TypeSubst.withCast(call, castType)
    }
  }

}
