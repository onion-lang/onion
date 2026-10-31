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
    extensionReceiverHint: ObjectType = null,
    calledStatically: Boolean = false
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
    // `Response::json(...)` on a nested class whose enclosing class has that static method
    // (`Server::json`): name the enclosing class so the hint can point there.
    val enclosingStaticOwner: String = targetType match {
      case ct: ClassType if calledStatically && ct.name.contains("$") =>
        val outerName = ct.name.substring(0, ct.name.lastIndexOf('$'))
        bodyContext.table.load(outerName) match {
          case Some(outer) if outer.methods(name).exists(m => Modifier.isStatic(m.modifier) && m.arguments.length == argTypes.length) =>
            outerName.substring(outerName.lastIndexOf('.') + 1)
          case _ => null
        }
      case _ => null
    }
    val hexDigitBHint: AnyRef =
      if (passesHexEndingInBForByte(node, targetType, name, argTypes.length)) SemanticErrorReporter.HexDigitBHint else null
    bodyContext.report(METHOD_NOT_FOUND, node, targetType, name, argTypes, java.lang.Boolean.valueOf(isUnqualifiedCall), extensionReceiverHint, inaccessibleFieldNamed, java.lang.Boolean.valueOf(calledStatically), enclosingStaticOwner, hexDigitBHint)
  }

  // `f(0xFFB)` where some `f` of that arity takes a Byte in that position: before #2324 the
  // literal was that Byte; now it is an Int, and the error should say so.
  private def passesHexEndingInBForByte(node: AST.Node, targetType: AnyRef, name: String, argc: Int): Boolean = {
    def isHexEndingInB(e: AST.Expression): Boolean = e match {
      case AST.IntegerLiteral(_, _, hexEndsInB) => hexEndsInB
      case AST.Negate(_, AST.IntegerLiteral(_, _, hexEndsInB)) => hexEndsInB
      case _ => false
    }
    val args: List[AST.Expression] = node match {
      case c: AST.MethodCall => c.args
      case c: AST.UnqualifiedMethodCall => c.args
      case c: AST.StaticMethodCall => c.args
      case _ => Nil
    }
    val positions = args.indices.filter(i => isHexEndingInB(args(i)))
    positions.nonEmpty && (targetType match {
      case obj: ObjectType =>
        obj.methods.exists { m =>
          m.name == name && m.arguments.length == argc && positions.exists(i => i < argc && m.arguments(i) == BasicType.BYTE)
        }
      case _ => false
    })
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
    secondArguments: Array[Type],
    hint: AnyRef = null
  ): Unit = {
    val first = Array[AnyRef](firstAffiliation, firstName, firstArguments)
    val second = Array[AnyRef](secondAffiliation, secondName, secondArguments)
    if (hint == null) bodyContext.report(AMBIGUOUS_METHOD, node, first, second)
    else bodyContext.report(AMBIGUOUS_METHOD, node, first, second, hint)
  }

  def reportIllegalMethodCall(
    node: AST.Node,
    method: Method,
    name: String
  ): Unit =
    bodyContext.report(ILLEGAL_METHOD_CALL, node, method.affiliation, name, method.arguments)
}
