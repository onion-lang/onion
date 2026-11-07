package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.SemanticError.*
import onion.compiler.typing.session.TypingBodyContext

import java.util.{TreeSet => JTreeSet}

import scala.jdk.CollectionConverters.*

private[compiler] final class SuperMethodCallSupport(
  bodyContext: TypingBodyContext,
  calls: MethodCallTyping
) {
  private val overloadSupport = new CallOverloadSupport(calls.typing, calls)

  def typeSuperMethodCall(node: AST.SuperMethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    if (ArgumentHelpers.hasNamedArguments(node.args)) return typeWithNamedArgs(node, context, expected)
    val parameters = calls.typedTerms(node.args.toArray, context)
    if (parameters == null) return None
    val contextClass = bodyContext.definition
    calls.tryFindMethod(node, contextClass.superClass, node.name, parameters) match {
      case Right(method) =>
        // The method may be declared further up (`Leaf[V] extends Mid[V]
        // extends Box[U]`), so substitute along the whole extends chain: with
        // the direct superclass's arguments alone, Box's `T` came back
        // unsubstituted (#2308).
        val classSubst = TypeSubstitution.hierarchySubstitution(contextClass.superClass, method.affiliation, selfReceiver = true)
        calls.buildResolvedCall(node, method, parameters, node.typeArgs, classSubst, expected)(
          expectedArgs => calls.prepareCallParams(node, node.args, method, parameters, expectedArgs),
          finalParams => new CallSuper(new This(contextClass), method, finalParams)
        )
      case Left(_) =>
        calls.reportMethodNotFound(node, contextClass, node.name, calls.types(parameters))
        None
    }
  }

  // `super.f(b = 5)`: bind by name like `this.f(b = 5)`; the positional path
  // below the guard would put 5 into the first parameter (#2650).
  private def typeWithNamedArgs(node: AST.SuperMethodCall, context: LocalContext, expected: Type): Option[Term] = {
    val contextClass = bodyContext.definition
    val candidates = new JTreeSet[Method](new MethodComparator)
    calls.collectMethodsMatching(contextClass.superClass, node.name, candidates, calls.isInstanceMethod)
    if (candidates.isEmpty) {
      calls.reportMethodNotFound(node, contextClass, node.name, Array[Type]())
      return None
    }
    overloadSupport.selectNamedArgumentMethod(candidates, node.args) match {
      case CandidateSelection.NoMatch =>
        ArgumentHelpers.findUnknownNamedArg(candidates.asScala, node.args) match {
          case Some(named) =>
            bodyContext.report(UNKNOWN_PARAMETER_NAME, named, named.name,
              ArgumentHelpers.parameterNameCandidates(candidates.asScala))
          case None => calls.reportMethodNotFound(node, contextClass, node.name, Array[Type]())
        }
        None
      case CandidateSelection.Ambiguous(first, second) =>
        calls.reportAmbiguousMethod(node, first, second, node.name)
        None
      case CandidateSelection.Selected(method) =>
        val classSubst = TypeSubstitution.hierarchySubstitution(contextClass.superClass, method.affiliation, selfReceiver = true)
        calls.processNamedArguments(node, node.args, method, context).flatMap { params =>
          calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected)(
            expectedArgs => calls.processParamsWithExpected(node, params, expectedArgs, method),
            finalParams => new CallSuper(new This(contextClass), method, finalParams)
          )
        }
    }
  }
}
