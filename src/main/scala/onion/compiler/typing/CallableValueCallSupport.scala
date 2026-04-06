package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

private[compiler] final class CallableValueCallSupport(
  typing: Typing,
  calls: MethodCallTyping
) {
  import typing.*

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
      val field = MemberAccess.findField(definition_, node.name)
      if (field != null && MemberAccess.isMemberAccessible(field, definition_)) {
        field.`type` match
          case targetType: ObjectType =>
            return callOnTarget(new RefField(new This(definition_), field), targetType)
          case _ =>
      }
    }

    None
  }
}
