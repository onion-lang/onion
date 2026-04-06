package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing

private[compiler] final class MemberSelectionTypingSupport(
  typing: Typing,
  calls: MethodCallTyping
) {
  import typing.*

  def typeMemberSelection(node: AST.MemberSelection, context: LocalContext): Option[Term] = {
    val contextClass = definition_
    var target = calls.typed(node.target, context).getOrElse(null)
    if (target == null) return None
    if (target.`type`.isNullType) {
      report(INCOMPATIBLE_TYPE, node.target, rootClass, target.`type`)
      return None
    }

    if (target.`type`.isBasicType) {
      val basicType = target.`type`.asInstanceOf[BasicType]
      if (basicType == BasicType.VOID) {
        report(INCOMPATIBLE_TYPE, node.target, rootClass, basicType)
        return None
      }
      target = Boxing.boxing(table_, target)
    }

    val targetType = target.`type`.asInstanceOf[ObjectType]
    if (!MemberAccess.ensureTypeAccessible(typing, node, targetType, contextClass)) return None
    val name = node.name
    if (target.`type`.isArrayType) {
      if (name.equals(MethodNames.LENGTH) || name.equals(MethodNames.SIZE)) {
        return Some(new ArrayLength(target))
      } else {
        return None
      }
    }
    val field = MemberAccess.findField(targetType, name)
    if (field != null && MemberAccess.isMemberAccessible(field, definition_)) {
      val ref = new RefField(target, field)
      return TypeSubst.withCastOpt(ref, TypeSubst.withClassOnly(ref.`type`, target.`type`))
    }

    val methodNames = Array(name, calls.getter(name), calls.getterBoolean(name))
    var methodIndex = 0
    while (methodIndex < methodNames.length) {
      val methodName = methodNames(methodIndex)
      calls.tryFindMethod(node, targetType, methodName, Array.empty) match {
        case Right(method) =>
          val call = new Call(target, method, Array.empty)
          return TypeSubst.withCastOpt(call, TypeSubst.withClassOnly(method.returnType, target.`type`))
        case Left(false) => return None
        case Left(true) =>
      }
      methodIndex += 1
    }

    if (field == null) report(FIELD_NOT_FOUND, node, targetType, node.name)
    else report(FIELD_NOT_ACCESSIBLE, node, targetType, node.name, definition_)
    None
  }
}
