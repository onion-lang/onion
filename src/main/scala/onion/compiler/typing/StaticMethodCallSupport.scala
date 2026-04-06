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
  import typing.*
  private val overloadSupport = new CallOverloadSupport(typing, calls)

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

  def typeStaticMethodCall(node: AST.StaticMethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    val typeRef = mapFrom(node.typeRef).asInstanceOf[ClassType]
    if (typeRef == null) return None

    if (ArgumentHelpers.hasNamedArguments(node.args)) {
      return typeStaticMethodCallWithNamedArgs(node, typeRef, context, expected)
    }

    val parameters = calls.typedTerms(node.args.toArray, context)
    if (parameters == null) {
      None
    } else if (node.typeArgs.nonEmpty) {
      val methods = typeRef.findMethod(node.name, parameters)
      if (methods.length == 0) {
        calls.reportMethodNotFound(node, typeRef, node.name, calls.types(parameters))
        None
      } else if (methods.length > 1) {
        calls.reportAmbiguousMethods(node, node.name, methods)
        None
      } else {
        val method = methods(0)
        val classSubst = TypeSubstitution.classSubstitution(typeRef)
        calls.buildResolvedCall(node, method, parameters, node.typeArgs, classSubst, expected)(
          expectedArgs => calls.prepareCallParams(node, node.args, method, parameters, expectedArgs),
          finalParams => new CallStatic(typeRef, method, finalParams)
        )
      }
    } else {
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
