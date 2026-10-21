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
      resolveQuiet(node, resolved, context, allowStaticSelf = false).orElse {
        // Neither found: let resolveMemberSelection emit the appropriate diagnostic.
        calls.resolveMemberSelection(node, resolved.targetType, node.name, reportErrorIfMissing = true)
          .flatMap(buildResolved(node, resolved, context, _, report = true, allowStaticSelf = false))
      }
    }
  }

  /**
   * Same resolution order as [[typeMemberSelection]] (field/getter, then
   * zero-arg extension) but reports nothing on a miss. Used as a probe by the
   * bare-identifier self-method fallback (a receiver-less, parens-less call to
   * another no-arg method of the same class, #2013), which still wants its own
   * diagnostic when neither a field nor a method resolves. This is the ONLY
   * caller allowed to resolve a static sibling (`allowStaticSelf = true`): a
   * user-written `self.name`/`this.name` reaches [[typeMemberSelection]]
   * instead, same as any other explicit receiver, and reports E0019 for a
   * static method just as `self.name()` already does (#2249 review).
   */
  private[typing] def tryMemberSelectionQuiet(node: AST.MemberSelection, context: LocalContext): Option[Term] = {
    val target = calls.typed(node.target, context).getOrElse(null)
    if (target == null) return None
    calls.normalizeMemberSelectionTarget(node, target).flatMap(resolveQuiet(node, _, context, allowStaticSelf = true))
  }

  private def resolveQuiet(
    node: AST.MemberSelection,
    resolved: ResolvedMemberSelectionTarget,
    context: LocalContext,
    allowStaticSelf: Boolean
  ): Option[Term] =
    // Resolution order: (1) regular field/getter lookup; (2) zero-arg extension
    // method (property-style `def name: T` in an `extension` block).  Extensions
    // come AFTER regular members so that a built-in like List.size() is not
    // shadowed by a Colls/Iterables extension of the same name.
    calls.resolveMemberSelection(node, resolved.targetType, node.name, reportErrorIfMissing = false) match {
      case Some(r) => buildResolved(node, resolved, context, r, report = false, allowStaticSelf)
      case None => calls.tryZeroArgExtensionAccess(node, node.name, resolved.term, resolved.targetType, null)
    }

  private def buildResolved(
    node: AST.MemberSelection,
    resolved: ResolvedMemberSelectionTarget,
    context: LocalContext,
    r: ResolvedMemberSelection,
    report: Boolean,
    allowStaticSelf: Boolean
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
      TypeSubst.withCastOpt(narrowedRef, inheritedType(narrowedRef.`type`, resolved.term, field.affiliation))
    case ResolvedGetterSelection(method) if Modifier.isStatic(method.modifier) =>
      if (allowStaticSelf) {
        // A bare name falling back to a zero-arg sibling (#2013) can land on
        // a static one -- e.g. a top-level script's own `def`s, which compile
        // as static members of the synthesized Main class. Call it the static
        // way; building an instance Call emits invokevirtual on a method the
        // codegen declared static, which the JVM rejects at link time with
        // IncompatibleClassChangeError (#2248). Only the bare-name fallback
        // (tryMemberSelectionQuiet's one caller) sets allowStaticSelf: a
        // user-written `self.name`/`this.name` falls to the branch below,
        // same as any other explicit receiver (#2249 review, round 2).
        val call: Term = new CallStatic(method.affiliation, method, Array.empty)
        TypeSubst.withCastOpt(call, inheritedType(method.returnType, resolved.term, method.affiliation))
      } else {
        // An explicit receiver, bare or not -- including a user-written
        // `self.staticMethod`/`this.staticMethod` -- selecting a static
        // method: calling it through an instance is illegal, same as the
        // explicit-parens form (`expr.staticMethod()`) already reports.
        // Reusing the #2013 fallback here would silently drop the receiver's
        // evaluation -- and its side effects -- instead (#2249 review).
        if (report) calls.reportIllegalMethodCall(node, method, node.name)
        None
      }
    case ResolvedGetterSelection(method) =>
      val call: Term = new Call(resolved.term, method, Array.empty)
      TypeSubst.withCastOpt(call, inheritedType(method.returnType, resolved.term, method.affiliation))
  }

  // The member may be declared on an ancestor (`IntBox extends Box[Int]`), so
  // substitute through the extends-clause chain, not just the receiver's own
  // type arguments (#2033).
  private def inheritedType(typ: Type, receiver: Term, declaring: ClassType): Type =
    {
      val classSubst = TypeSubstitution.hierarchySubstitution(receiver.`type`, declaring, receiver.isInstanceOf[This])
      TypeSubst.result(typ, classSubst, Map.empty)
    }
}
