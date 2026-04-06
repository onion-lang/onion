package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing

private[compiler] final class SafeNavigationTypingSupport(
  typing: Typing,
  calls: MethodCallTyping
) {
  import typing.*

  def typeSafeMemberSelection(node: AST.SafeMemberSelection, context: LocalContext): Option[Term] = {
    var target = calls.typed(node.target, context).getOrElse(null)
    if (target == null) return None

    val targetType = target.`type` match {
      case nullableType: NullableType => nullableType.innerType
      case other => other
    }

    if (targetType.isNullType) {
      report(INCOMPATIBLE_TYPE, node.target, rootClass, target.`type`)
      return None
    }

    val (finalTarget, finalTargetType) = targetType match {
      case basicType: BasicType =>
        if (basicType == BasicType.VOID) {
          report(INCOMPATIBLE_TYPE, node.target, rootClass, basicType)
          return None
        }
        val boxed = Boxing.boxing(table_, target)
        (boxed, boxed.`type`.asInstanceOf[ObjectType])
      case objectType: ObjectType =>
        (target, objectType)
      case _ =>
        report(INCOMPATIBLE_TYPE, node.target, rootClass, targetType)
        return None
    }

    if (!MemberAccess.ensureTypeAccessible(typing, node, finalTargetType, definition_)) return None
    val name = node.name

    if (finalTargetType.isArrayType) {
      if (name.equals(MethodNames.LENGTH) || name.equals(MethodNames.SIZE)) {
        val lengthField = new FieldDefinition(node.location, 0, null, "length", BasicType.INT)
        return Some(new SafeFieldAccess(node.location, finalTarget, lengthField))
      } else {
        return None
      }
    }

    val field = MemberAccess.findField(finalTargetType, name)
    if (field != null && MemberAccess.isMemberAccessible(field, definition_)) {
      return Some(new SafeFieldAccess(node.location, finalTarget, field))
    }

    val methodNames = Array(name, calls.getter(name), calls.getterBoolean(name))
    var methodIndex = 0
    while (methodIndex < methodNames.length) {
      val methodName = methodNames(methodIndex)
      calls.tryFindMethod(node, finalTargetType, methodName, Array.empty) match {
        case Right(method) =>
          return Some(new SafeCall(node.location, finalTarget, method, Array.empty))
        case Left(false) => return None
        case Left(true) =>
      }
      methodIndex += 1
    }

    if (field == null) report(FIELD_NOT_FOUND, node, finalTargetType, node.name)
    else report(FIELD_NOT_ACCESSIBLE, node, finalTargetType, node.name, definition_)
    None
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
    val methods = MethodResolution.findMethods(targetType, name, params, table_)
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
