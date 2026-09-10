package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

private[compiler] final class SafeNavigationTypingSupport(
  bodyContext: TypingBodyContext,
  calls: MethodCallTyping,
  fallback: MethodCallFallbackSupport
) {
  def typeSafeMemberSelection(node: AST.SafeMemberSelection, context: LocalContext): Option[Term] = {
    val target = calls.typed(node.target, context).getOrElse(null)
    if (target == null) return None

    calls.normalizeSafeMemberSelectionTarget(node, target).flatMap { resolved =>
      calls.resolveMemberSelection(node, resolved.targetType, node.name).map {
        case ResolvedArrayLengthSelection =>
          val lengthField = new FieldDefinition(node.location, 0, null, "length", BasicType.INT)
          new SafeFieldAccess(node.location, resolved.term, lengthField)
        case ResolvedFieldSelection(field) =>
          new SafeFieldAccess(node.location, resolved.term, field)
        case ResolvedGetterSelection(method) =>
          new SafeCall(node.location, resolved.term, method, Array.empty)
      }
    }
  }

  def typeSafeMethodCall(node: AST.SafeMethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    val target = calls.typed(node.target, context).getOrElse(null)
    if (target == null) return None

    val params = calls.typedTerms(node.args.toArray, context)
    if (params == null) return None

    calls.normalizeSafeMethodCallTarget(node, target).flatMap { resolved =>
      typeSafeMethodCallOnObject(node, resolved.term, resolved.targetType, params, expected)
    }
  }

  private def typeSafeMethodCallOnObject(
    node: AST.SafeMethodCall,
    target: Term,
    targetType: ObjectType,
    params: Array[Term],
    expected: Type
  ): Option[Term] = {
    val name = node.name
    val methods = MethodResolution.findMethods(targetType, name, params, bodyContext.table)
    if (methods.length == 0) {
      // No instance method found — try extension methods before reporting.
      // For the null-safe call, we need two terms:
      //   nullTarget    — the nullable value to null-check (the target as-is, which may be
      //                   String? or the AsInstanceOf-boxed Integer for a nullable primitive)
      //   nonNullTarget — a non-nullable view of the receiver for the static extension call
      val (nullTarget, nonNullTarget) = target.`type` match {
        case nt: NullableType =>
          // Reference nullable (e.g. String?): strip the NullableType wrapper via a cast
          val inner = nt.innerType.asInstanceOf[ObjectType]
          (target, new AsInstanceOf(target, inner))
        case _ =>
          // Already an ObjectType (nullable primitive was boxed by normalizeSafeMethodCallTarget,
          // e.g. AsInstanceOf(n, Integer) for Int?): null-check the boxed value directly
          (target, target)
      }
      val ext = fallback.tryExtensionSafeMethodCall(node, nullTarget, nonNullTarget, targetType, params, expected)
      if (ext.isDefined) return ext
      calls.reportMethodNotFound(node, targetType, name, calls.types(params))
      return None
    }

    calls.selectSingleMethod(node, targetType, name, methods, calls.types(params)) match {
      case None => None
      case Some(method) if (method.modifier & AST.M_STATIC) != 0 =>
        calls.reportIllegalMethodCall(node, method, name)
        None
      case Some(method) =>
        val classSubst = TypeSubstitution.hierarchySubstitution(targetType, method.affiliation)
        calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected)(
          expectedArgs => calls.prepareCallParams(node, node.args, method, params, expectedArgs),
          finalParams => new SafeCall(node.location, target, method, finalParams)
        )
    }
  }
}
