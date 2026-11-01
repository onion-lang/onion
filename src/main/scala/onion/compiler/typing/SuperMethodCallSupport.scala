package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

private[compiler] final class SuperMethodCallSupport(
  bodyContext: TypingBodyContext,
  calls: MethodCallTyping
) {
  def typeSuperMethodCall(node: AST.SuperMethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
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
}
