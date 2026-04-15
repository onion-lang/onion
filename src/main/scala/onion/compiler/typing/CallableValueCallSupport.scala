package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

private[compiler] final class CallableValueCallSupport(
  bodyContext: TypingBodyContext,
  calls: MethodCallTyping
) {
  def resolveCallableValue(
    node: AST.UnqualifiedMethodCall,
    params: Array[Term],
    context: LocalContext,
    expected: Type
  ): Option[Term] = {
    if (node.typeArgs.nonEmpty) return None

    def callOnTarget(target: Term, targetType: ObjectType): Option[Term] = {
      if (targetType.methods("call").isEmpty) return None
      val callNode = new AST.MethodCall(node.location, new AST.Id(node.location, node.name), "call", node.args, Nil)
      calls.typeMethodCallOnObject(callNode, target, targetType, params, context, expected)
    }

    val local = context.lookup(node.name)
    if (local != null) {
      context.recordUsage(node.name)
      local.tp match
        case targetType: ObjectType => return callOnTarget(new RefLocal(local), targetType)
        case _ => return None
    }

    if (!context.isStatic) {
      val owner = bodyContext.definition
      val field = MemberAccess.findField(owner, node.name)
      if (field != null && MemberAccess.isMemberAccessible(field, owner)) {
        field.`type` match
          case targetType: ObjectType =>
            return callOnTarget(new RefField(new This(owner), field), targetType)
          case _ =>
      }
    }

    None
  }
}
