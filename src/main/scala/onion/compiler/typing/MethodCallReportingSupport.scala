package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

private[compiler] final class MethodCallReportingSupport(
  typing: Typing,
  calls: MethodCallTyping
) {
  import typing.*

  def reportMethodNotFound(
    node: AST.Node,
    targetType: AnyRef,
    name: String,
    argTypes: Array[Type]
  ): Unit =
    report(METHOD_NOT_FOUND, node, targetType, name, argTypes)

  def reportAmbiguousMethods(
    node: AST.Node,
    name: String,
    methods: Array[Method]
  ): Unit =
    reportAmbiguousMethod(node, methods(0), methods(1), name)

  def reportAmbiguousMethod(
    node: AST.Node,
    first: Method,
    second: Method,
    name: String = null
  ): Unit = {
    val methodName = if (name != null) name else first.name
    reportAmbiguousSignature(node, first.affiliation, methodName, first.arguments, second.affiliation, methodName, second.arguments)
  }

  def reportAmbiguousSignature(
    node: AST.Node,
    firstAffiliation: AnyRef,
    firstName: String,
    firstArguments: Array[Type],
    secondAffiliation: AnyRef,
    secondName: String,
    secondArguments: Array[Type]
  ): Unit = {
    report(
      AMBIGUOUS_METHOD,
      node,
      Array[AnyRef](firstAffiliation, firstName, firstArguments),
      Array[AnyRef](secondAffiliation, secondName, secondArguments)
    )
  }

  def reportIllegalMethodCall(
    node: AST.Node,
    method: Method,
    name: String
  ): Unit =
    report(ILLEGAL_METHOD_CALL, node, method.affiliation, name, method.arguments)
}
