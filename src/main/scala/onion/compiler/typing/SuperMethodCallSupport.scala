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
      case Left(true) =>
        // Not in the superclass chain: a default method of one of the class's
        // own interfaces, as Java's `I.super.m()` reaches it (#2675).
        interfaceDefault(node, contextClass, parameters) match {
          case Right((iface, method)) =>
            val classSubst = TypeSubstitution.hierarchySubstitution(iface, method.affiliation, selfReceiver = true)
            calls.buildResolvedCall(node, method, parameters, node.typeArgs, classSubst, expected)(
              expectedArgs => calls.prepareCallParams(node, node.args, method, parameters, expectedArgs),
              finalParams => new CallSuper(null, new This(contextClass), method, finalParams, iface)
            )
          case Left(true) =>
            calls.reportMethodNotFound(node, contextClass, node.name, calls.types(parameters))
            None
          case Left(_) => None // already reported (ambiguous or inaccessible)
        }
      case Left(_) =>
        calls.reportMethodNotFound(node, contextClass, node.name, calls.types(parameters))
        None
    }
  }

  /** The one default (non-abstract) method `name` among the direct interfaces,
   *  paired with the interface it is reached through. Two different defaults
   *  are ambiguous (E0006), as in Java. `Left(true)`: none found, nothing
   *  reported; `Left(false)`: an error was reported. */
  private def interfaceDefault(node: AST.SuperMethodCall, contextClass: ClassType, parameters: Array[Term]): Either[Boolean, (ClassType, Method)] = {
    val found = contextClass.interfaces.toList.flatMap { iface =>
      MethodResolution.findMethods(iface, node.name, parameters, bodyContext.table).toList
        .filter(m => !Modifier.isAbstract(m.modifier) && !Modifier.isStatic(m.modifier))
        .map(m => (iface, m))
    }
    val distinct = found.foldLeft(List.empty[(ClassType, Method)]) { (acc, f) =>
      if (acc.exists(_._2 eq f._2)) acc else acc :+ f
    }
    distinct match {
      case Nil => Left(true)
      case one :: Nil =>
        if (!MemberAccess.isMemberAccessible(one._2, contextClass)) {
          bodyContext.report(SemanticError.METHOD_NOT_ACCESSIBLE, node, one._2.affiliation, node.name, one._2.arguments, contextClass)
          Left(false)
        } else Right(one)
      case a :: b :: _ =>
        bodyContext.report(
          SemanticError.AMBIGUOUS_METHOD,
          node,
          Array[AnyRef](a._2.affiliation, node.name, a._2.arguments),
          Array[AnyRef](b._2.affiliation, node.name, b._2.arguments)
        )
        Left(false)
    }
  }
}
