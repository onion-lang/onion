package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

private[compiler] final class MemberSelectionTypingSupport(
  bodyContext: TypingBodyContext,
  calls: MethodCallTyping
) {
  def typeMemberSelection(node: AST.MemberSelection, context: LocalContext): Option[Term] = {
    val target = calls.typed(node.target, context).getOrElse(null)
    if (target == null) return None

    calls.normalizeMemberSelectionTarget(node, target).flatMap { resolved =>
      calls.resolveMemberSelection(node, resolved.targetType, node.name).flatMap {
        case ResolvedArrayLengthSelection =>
          Some(new ArrayLength(resolved.term))
        case ResolvedFieldSelection(field) =>
          val ref = new RefField(resolved.term, field)
          TypeSubst.withCastOpt(ref, TypeSubst.withClassOnly(ref.`type`, resolved.term.`type`))
        case ResolvedGetterSelection(method) =>
          val call = new Call(resolved.term, method, Array.empty)
          TypeSubst.withCastOpt(call, TypeSubst.withClassOnly(method.returnType, resolved.term.`type`))
      }
    }
  }
}
