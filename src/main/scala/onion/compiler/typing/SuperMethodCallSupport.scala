package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

private[compiler] final class SuperMethodCallSupport(
  typing: Typing,
  calls: MethodCallTyping
) {
  import typing.*

  def typeSuperMethodCall(node: AST.SuperMethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    val parameters = calls.typedTerms(node.args.toArray, context)
    if (parameters == null) return None
    val contextClass = definition_
    calls.tryFindMethod(node, contextClass.superClass, node.name, parameters) match {
      case Right(method) =>
        val classSubst = TypeSubstitution.classSubstitution(contextClass.superClass)
        calls.buildResolvedCall(node, method, parameters, node.typeArgs, classSubst, expected)(
          expectedArgs => calls.prepareCallParams(node, node.args, method, parameters, expectedArgs),
          finalParams => new CallSuper(new This(contextClass), method, finalParams)
        )
      case Left(_) =>
        calls.reportMethodNotFound(node, contextClass, node.name, calls.types(parameters))
        None
    }
  }
}
