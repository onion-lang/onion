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
    expected: Type
  ): Option[Term] =
    extensionMethodFallbackSupport.tryExtensionMethodCall(node, target, targetType, params, expected)

  def typeMethodCallWithBidirectionalInference(
    node: AST.MethodCall,
    target: Term,
    targetType: ObjectType,
    context: LocalContext,
    expected: Type,
    untypedClosureIndices: Set[Int]
  ): Option[Term] = {
    val name = node.name
    val args = node.args.toArray

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

    val candidates = new JTreeSet[Method](new MethodComparator)
    calls.collectMethodsMatching(targetType, name, candidates, calls.isInstanceMethod)

    if (candidates.isEmpty) {
      extensionMethodFallbackSupport.tryExtensionMethodCallBidirectional(
        node, target, targetType, context, expected, untypedClosureIndices
      ) match {
        case some @ Some(_) => return some
        case None =>
          calls.reportMethodNotFound(node, targetType, name, Array[Type]())
          return None
      }
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
      extensionMethodFallbackSupport.tryExtensionMethodCallBidirectional(
        node, target, targetType, context, expected, untypedClosureIndices
      ) match {
        case some @ Some(_) => return some
        case None =>
          calls.reportMethodNotFound(node, targetType, name, nonClosureTypes.values.toArray)
          return None
      }
    }

    val method = overloadSupport.selectMostSpecificApplicable(
      applicableMethods,
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
    val classSubst = TypeSubstitution.classSubstitution(target.`type`)
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
      finalProcessedParams <- calls.prepareCallParams(node, node.args, method, finalParams, finalExpectedArgs)
    } yield {
      val call = new Call(target, method, finalProcessedParams)
      val castType = TypeSubst(method.returnType, classSubst, finalMethodSubst)
      TypeSubst.withCast(call, castType)
    }
  }

}
