package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

private[compiler] final class MemberSelectionTypingSupport(
  calls: MethodCallTyping
) {
  def typeMemberSelection(node: AST.MemberSelection, context: LocalContext): Option[Term] = {
    val target = calls.typed(node.target, context).getOrElse(null)
    if (target == null) return None

    calls.normalizeMemberSelectionTarget(node, target).flatMap { resolved =>
      resolveQuiet(node, resolved, context).orElse {
        // Neither found: let resolveMemberSelection emit the appropriate diagnostic.
        calls.resolveMemberSelection(node, resolved.targetType, node.name, reportErrorIfMissing = true)
          .flatMap(buildResolved(node, resolved, context, _))
      }
    }
  }

  /**
   * Same resolution order as [[typeMemberSelection]] (field/getter, then
   * zero-arg extension) but reports nothing on a miss. Used as a probe by the
   * bare-identifier self-method fallback (a receiver-less, parens-less call to
   * another no-arg method of the same class, #2013), which still wants its own
   * diagnostic when neither a field nor a method resolves.
   */
  private[typing] def tryMemberSelectionQuiet(node: AST.MemberSelection, context: LocalContext): Option[Term] = {
    val target = calls.typed(node.target, context).getOrElse(null)
    if (target == null) return None
    calls.normalizeMemberSelectionTarget(node, target).flatMap(resolveQuiet(node, _, context))
  }

  private def resolveQuiet(
    node: AST.MemberSelection,
    resolved: ResolvedMemberSelectionTarget,
    context: LocalContext
  ): Option[Term] =
    // Resolution order: (1) regular field/getter lookup; (2) zero-arg extension
    // method (property-style `def name: T` in an `extension` block).  Extensions
    // come AFTER regular members so that a built-in like List.size() is not
    // shadowed by a Colls/Iterables extension of the same name.
    calls.resolveMemberSelection(node, resolved.targetType, node.name, reportErrorIfMissing = false) match {
      case Some(r) => buildResolved(node, resolved, context, r)
      case None => calls.tryZeroArgExtensionAccess(node, node.name, resolved.term, resolved.targetType, null)
    }

  private def buildResolved(
    node: AST.MemberSelection,
    resolved: ResolvedMemberSelectionTarget,
    context: LocalContext,
    r: ResolvedMemberSelection
  ): Option[Term] = r match {
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
