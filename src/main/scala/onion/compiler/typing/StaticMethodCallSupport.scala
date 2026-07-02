package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

import java.util.{TreeSet => JTreeSet}

import scala.jdk.CollectionConverters.*

private[compiler] final class StaticMethodCallSupport(
  typing: Typing,
  calls: MethodCallTyping
) {
  private val overloadSupport = new CallOverloadSupport(typing, calls)

  private def classTypeOf(typeNode: AST.TypeNode): Option[ClassType] =
    typing.mapFrom(typeNode) match {
      case Some(ct: ClassType) => Some(ct)
      case Some(other) =>
        typing.report(INCOMPATIBLE_TYPE, typeNode, typing.rootClass, other)
        None
      case None => None
    }

  def typeStaticMemberSelection(node: AST.StaticMemberSelection): Option[Term] =
    classTypeOf(node.typeRef).flatMap { typeRef =>
      val field = MemberAccess.findField(typeRef, node.name)
      if (field == null) {
        typing.report(FIELD_NOT_FOUND, node, typeRef, node.name)
        None
      } else {
        Some(new RefStaticField(typeRef, field))
      }
    }

  def typeStaticMethodCall(node: AST.StaticMethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    val typeRefOpt = classTypeOf(node.typeRef)
    if (typeRefOpt.isEmpty) return None
    val typeRef = typeRefOpt.get

    if (ArgumentHelpers.hasNamedArguments(node.args)) {
      return typeStaticMethodCallWithNamedArgs(node, typeRef, context, expected)
    }

    // Closures with untyped parameters cannot be typed before the target
    // parameter type is known: resolve the method from the other arguments
    // first, then type the closures against the selected parameter types.
    val untypedClosureIndices = node.args.zipWithIndex.collect {
      case (closure: AST.ClosureExpression, i) if closure.args.exists(_.typeRef == null) => i
    }.toSet
    if (untypedClosureIndices.nonEmpty && node.typeArgs.isEmpty) {
      return typeStaticCallWithBidirectionalInference(node, typeRef, context, expected, untypedClosureIndices)
    }

    val parameters = calls.typedTerms(node.args.toArray, context)
    if (parameters == null) {
      None
    } else {
      // Both the explicit-type-argument and the inferred cases go through the
      // boxing-aware applicable-method collection; buildResolvedCall applies
      // node.typeArgs. (A strict findMethod here would reject a primitive
      // argument against a type-variable parameter, e.g. Util::identity[Int](99).)
      val candidates = new JTreeSet[Method](new MethodComparator)
      calls.collectMethodsMatching(typeRef, node.name, candidates, calls.isStaticMethod)
      if (candidates.isEmpty) {
        calls.reportMethodNotFound(node, typeRef, node.name, calls.types(parameters))
        return None
      }

      val applicable = overloadSupport.collectStaticApplicables(
        typeRef,
        candidates.asScala,
        node,
        parameters,
        expected
      )

      def finalizeSelection(selected: ApplicableMethod): Option[Term] = {
        val classSubst = TypeSubstitution.classSubstitution(typeRef)
        calls.buildResolvedCall(node, selected.method, parameters, node.typeArgs, classSubst, expected)(
          expectedArgs => calls.prepareCallParams(node, node.args, selected.method, parameters, expectedArgs),
          finalParams => new CallStatic(typeRef, selected.method, finalParams)
        )
      }

      if (applicable.isEmpty) {
        val closureIndices = node.args.zipWithIndex.collect {
          case (expr, i) if expr.isInstanceOf[AST.ClosureExpression] => i
        }.toSet
        if (closureIndices.nonEmpty) {
          return typeStaticCallWithBidirectionalInference(node, typeRef, context, expected, closureIndices)
        }
        calls.reportMethodNotFound(node, typeRef, node.name, calls.types(parameters))
        None
      } else {
        overloadSupport.selectMostSpecificApplicable(applicable) match {
          case CandidateSelection.Selected(selected) =>
            finalizeSelection(selected)
          case CandidateSelection.Ambiguous(first, second) =>
            calls.reportAmbiguousMethod(node, first.method, second.method, node.name)
            None
          case CandidateSelection.NoMatch =>
            calls.reportMethodNotFound(node, typeRef, node.name, calls.types(parameters))
            None
        }
      }
    }
  }

  private def typeStaticCallWithBidirectionalInference(
    node: AST.StaticMethodCall,
    typeRef: ClassType,
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
    calls.collectMethodsMatching(typeRef, name, candidates, calls.isStaticMethod)
    if (candidates.isEmpty) {
      calls.reportMethodNotFound(node, typeRef, name, Array[Type]())
      return None
    }

    val nonClosureTypes = preliminaryParams.zipWithIndex.collect {
      case (term, i) if term != null => (i, term.`type`)
    }.toMap

    val applicableMethods = overloadSupport.collectPartialInstanceApplicables(
      typeRef,
      candidates.asScala,
      node,
      preliminaryParams,
      expected
    )
    if (applicableMethods.isEmpty) {
      calls.reportMethodNotFound(node, typeRef, name, nonClosureTypes.values.toArray)
      return None
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
      case CandidateSelection.Selected(selected) => selected.method
      case CandidateSelection.Ambiguous(first, second) =>
        calls.reportAmbiguousMethod(node, first.method, second.method, name)
        return None
      case CandidateSelection.NoMatch =>
        calls.reportMethodNotFound(node, typeRef, name, nonClosureTypes.values.toArray)
        return None
    }

    val classSubst = TypeSubstitution.classSubstitution(typeRef)
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
        }
      } else {
        finalParams(i) = preliminaryParams(i)
      }
    }
    if (hasError) return None

    val finalMethodSubst = GenericMethodTypeArguments.infer(typing, node, method, finalParams, classSubst, expected)
    val finalExpectedArgs = TypeSubst.args(method, classSubst, finalMethodSubst)

    for {
      finalProcessedParams <- calls.prepareCallParams(node, node.args, method, finalParams, finalExpectedArgs)
    } yield {
      val call = new CallStatic(typeRef, method, finalProcessedParams)
      val castType = TypeSubst.result(method.returnType, classSubst, finalMethodSubst)
      TypeSubst.withCast(call, castType)
    }
  }

  private def typeStaticMethodCallWithNamedArgs(
    node: AST.StaticMethodCall,
    typeRef: ClassType,
    context: LocalContext,
    expected: Type
  ): Option[Term] = {
    val candidates = new JTreeSet[Method](new MethodComparator)
    calls.collectMethodsMatching(typeRef, node.name, candidates, calls.isStaticMethod)
    if (candidates.isEmpty) {
      calls.reportMethodNotFound(node, typeRef, node.name, Array[Type]())
      return None
    }

    overloadSupport.selectNamedArgumentMethod(candidates, node.args) match {
      case CandidateSelection.NoMatch =>
        calls.reportMethodNotFound(node, typeRef, node.name, Array[Type]())
        None
      case CandidateSelection.Ambiguous(first, second) =>
        calls.reportAmbiguousMethod(node, first, second, node.name)
        None
      case CandidateSelection.Selected(method) =>
        val classSubst = TypeSubstitution.classSubstitution(typeRef)
        calls.processNamedArguments(node, node.args, method, context).flatMap { params =>
          calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected)(
            expectedArgs => calls.processParamsWithExpected(node, params, expectedArgs),
            finalParams => new CallStatic(typeRef, method, finalParams)
          )
        }
    }
  }
}
