package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

private[typing] object MemberAccess {
  private def hasSamePackage(a: ClassType, b: ClassType): Boolean = {
    var name1 = a.name
    var name2 = b.name
    var index: Int = 0
    index = name1.lastIndexOf(".")
    if (index >= 0) name1 = name1.substring(0, index)
    else name1 = ""
    index = name2.lastIndexOf(".")
    name2 = if (index >= 0) name2.substring(0, index) else ""
    name1 == name2
  }

  def isTypeAccessible(target: ClassType, context: ClassType): Boolean =
    if (hasSamePackage(target, context)) true else (target.modifier & AST.M_INTERNAL) == 0

  def isMemberAccessible(member: MemberRef, context: ClassType): Boolean = {
    val targetType = member.affiliation
    val modifier = member.modifier
    if (targetType == context) {
      true
    } else if (TypeRules.isSuperType(targetType, context)) {
      (modifier & AST.M_PROTECTED) != 0 || (modifier & AST.M_PUBLIC) != 0
    } else {
      (AST.M_PUBLIC & modifier) != 0
    }
  }

  def findField(target: ObjectType, name: String): FieldRef =
    if (target == null) {
      null
    } else {
      val direct = target.field(name)
      if (direct != null) {
        direct
      } else {
        val fromSuper = findField(target.superClass, name)
        if (fromSuper != null) {
          fromSuper
        } else {
          target.interfaces.iterator
            .map(findField(_, name))
            .find(_ != null)
            .getOrElse(null)
        }
      }
    }

  def ensureTypeAccessible(typing: Typing, node: AST.Node, target: ObjectType, context: ClassType): Boolean = {
    import typing.*
    if (target.isArrayType) {
      val component = target.asInstanceOf[ArrayType].component
      if (!component.isBasicType) {
        if (!isTypeAccessible(component.asInstanceOf[ClassType], context)) {
          report(CLASS_NOT_ACCESSIBLE, node, target, context)
          return false
        }
      }
    } else {
      if (!isTypeAccessible(target.asInstanceOf[ClassType], context)) {
        report(CLASS_NOT_ACCESSIBLE, node, target, context)
        return false
      }
    }
    true
  }
}

