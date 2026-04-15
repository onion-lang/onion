package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

private[compiler] final class SafeNavigationTypingSupport(
  bodyContext: TypingBodyContext,
  calls: MethodCallTyping
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
      calls.reportMethodNotFound(node, targetType, name, calls.types(params))
      return None
    }

    calls.selectSingleMethod(node, targetType, name, methods, calls.types(params)) match {
      case None => None
      case Some(method) if (method.modifier & AST.M_STATIC) != 0 =>
        calls.reportIllegalMethodCall(node, method, name)
        None
      case Some(method) =>
        val classSubst = TypeSubstitution.classSubstitution(targetType)
        calls.buildResolvedCall(node, method, params, node.typeArgs, classSubst, expected)(
          expectedArgs => calls.prepareCallParams(node, node.args, method, params, expectedArgs),
          finalParams => new SafeCall(node.location, target, method, finalParams)
        )
    }
  }
}
