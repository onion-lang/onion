package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

private[compiler] final class MethodCallReportingSupport(
  bodyContext: TypingBodyContext
) {
  def reportMethodNotFound(
    node: AST.Node,
    targetType: AnyRef,
    name: String,
    argTypes: Array[Type],
    isUnqualifiedCall: Boolean = false,
    extensionReceiverHint: ObjectType = null
  ): Unit = {
    // A same-named field that exists but isn't accessible from here (e.g. Concurrent.Lock's
    // private `lock`) is not a usable fix for "method not found" -- the paren-less-field hint
    // in SemanticErrorReporter would just steer the user into a second, equally unhelpful
    // FIELD_NOT_ACCESSIBLE. Resolve accessibility here, where MemberAccess (typing-package
    // private) and bodyContext.definition (the calling class) are both in scope.
    val inaccessibleFieldNamed: java.lang.Boolean = targetType match {
      case obj: ObjectType =>
        obj.fields.find(_.name == name) match {
          case Some(f) => java.lang.Boolean.valueOf(!MemberAccess.isMemberAccessible(f, bodyContext.definition))
          case None => java.lang.Boolean.FALSE
        }
      case _ => java.lang.Boolean.FALSE
    }
    bodyContext.report(METHOD_NOT_FOUND, node, targetType, name, argTypes, java.lang.Boolean.valueOf(isUnqualifiedCall), extensionReceiverHint, inaccessibleFieldNamed)
  }

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
    bodyContext.report(
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
    bodyContext.report(ILLEGAL_METHOD_CALL, node, method.affiliation, name, method.arguments)
}
