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
          // Smart-cast an explicit `this.field` / `self.field` read of a final
          // (`val`) field narrowed by a preceding null check, mirroring the
          // bare-name path in SimpleExpressionTypingSupport. Only final fields
          // are eligible -- a `var` could change between the check and the use.
          val narrowedRef: Term =
            node.target match {
              case _: AST.CurrentInstance if Modifier.isFinal(field.modifier) =>
                context.getFieldNarrowing(node.name) match {
                  case Some(nt) if nt != ref.`type` => new AsInstanceOf(ref, nt)
                  case _ => ref
                }
              case _ => ref
            }
          TypeSubst.withCastOpt(narrowedRef, TypeSubst.withClassOnly(narrowedRef.`type`, resolved.term.`type`))
        case ResolvedGetterSelection(method) =>
          val call = new Call(resolved.term, method, Array.empty)
          TypeSubst.withCastOpt(call, TypeSubst.withClassOnly(method.returnType, resolved.term.`type`))
      }
    }
  }
}
