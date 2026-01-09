package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing

final class AssignmentTyping(private val typing: Typing, private val body: TypingBodyPass) {
  import typing.*

  def processLocalAssign(node: AST.Assignment, context: LocalContext): Term = {
    var value: Term = typed(node.rhs, context).getOrElse(null)
    if (value == null) return null
    val id = node.lhs.asInstanceOf[AST.Id]
    val bind = context.lookup(id.name)
    if (bind == null) {
      report(VARIABLE_NOT_FOUND, id, id.name, context.allNames.toArray)
      return null
    }
    if (!bind.isMutable) {
      report(CANNOT_ASSIGN_TO_VAL, id, id.name)
      return null
    }

    if (value.`type`.isBottomType) return value

    val frame = bind.frameIndex
    val index = bind.index
    val leftType = bind.tp
    value = processAssignable(node.rhs, leftType, value)
    if (value != null) new SetLocal(frame, index, leftType, value) else null
  }

  def processArrayAssign(node: AST.Assignment, context: LocalContext): Term = {
    var value = typed(node.rhs, context).getOrElse(null)
    val indexing = node.lhs.asInstanceOf[AST.Indexing]
    val target = typed(indexing.lhs, context).getOrElse(null)
    var index = typed(indexing.rhs, context).getOrElse(null)
    if (value == null || target == null || index == null) return null
    if (target.isBasicType) {
      report(INCOMPATIBLE_TYPE, indexing.lhs, rootClass, target.`type`)
      return null
    }
    if (target.isArrayType) {
      val targetType = target.`type`.asInstanceOf[ArrayType]
      // Try to unbox Integer to int for array index
      if (!index.isBasicType) {
        Boxing.unboxedType(table_, index.`type`) match {
          case Some(bt) if bt.isInteger => index = Boxing.unboxing(table_, index, bt)
          case _ => // will fail below
        }
      }
      if (!(index.isBasicType && index.`type`.asInstanceOf[BasicType].isInteger)) {
        report(INCOMPATIBLE_TYPE, indexing.rhs, BasicType.INT, index.`type`)
        return null
      }
      if (value.`type`.isBottomType) return value
      value = processAssignable(node.rhs, targetType.base, value)
      if (value == null) return null
      new SetArray(target, index, value)
    } else {
      val params = Array[Term](index, value)
      tryFindMethod(node, target.`type`.asInstanceOf[ObjectType], "set", Array[Term](index, value)) match {
        case Left(_) =>
          report(METHOD_NOT_FOUND, node, target.`type`, "set", types(params))
          if (value.`type`.isBottomType) value else null
        case Right(method) =>
          if (value.`type`.isBottomType) value else new Call(target, method, params)
      }
    }
  }

  def processMemberAssign(node: AST.Assignment, context: LocalContext): Term = {
    node match {
      case AST.Assignment(_, selection@AST.MemberSelection(_, _, _), expression) =>
        val contextClass = definition_
        val target = typed(selection.target, context).getOrElse(null)
        if (target == null) return null
        if (target.`type`.isBasicType || target.`type`.isNullType) {
          report(INCOMPATIBLE_TYPE, selection.target, rootClass, target.`type`)
          return null
        }
        val targetType = target.`type`.asInstanceOf[ObjectType]
        if (!MemberAccess.ensureTypeAccessible(typing, selection, targetType, contextClass)) return null
        val name = selection.name
        val field: FieldRef = MemberAccess.findField(targetType, name)
        val value: Term = typed(expression, context).getOrElse(null)
        if (field != null && MemberAccess.isMemberAccessible(field, definition_)) {
          if (Modifier.isFinal(field.modifier) && (context.constructor == null || !target.isInstanceOf[This])) {
            report(CANNOT_ASSIGN_TO_VAL, selection, field.name)
            return null
          }
          val classSubst = TypeSubstitution.classSubstitution(target.`type`)
          val expected = TypeSubstitution.substituteType(field.`type`, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true)
          if (value.`type`.isBottomType) return value
          val term = processAssignable(expression, expected, value)
          if (term == null) return null
          return new SetField(target, field, term)
        }
        tryFindMethod(selection, targetType, setter(name), Array[Term](value)) match {
          case Right(method) =>
            if (value.`type`.isBottomType) value else new Call(target, method, Array[Term](value))
          case Left(_) =>
            if (value.`type`.isBottomType) value else null
        }
      case _ =>
        report(UNIMPLEMENTED_FEATURE, node)
        null
    }
  }

  def typeAssignment(node: AST.Assignment, context: LocalContext): Option[Term] =
    node.lhs match {
      case _: AST.Id =>
        Option(processLocalAssign(node, context))
      // Removed: UnqualifiedFieldReference case - use this.field or self.field instead
      case _: AST.Indexing =>
        Option(processArrayAssign(node, context))
      case _: AST.MemberSelection =>
        Option(processMemberAssign(node, context))
      case _ =>
        None
    }

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)

  private def processAssignable(node: AST.Node, expected: Type, term: Term): Term =
    body.processAssignable(node, expected, term)

  private def tryFindMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Either[Boolean, Method] =
    body.tryFindMethod(node, target, name, params)

  private def types(terms: Array[Term]): Array[Type] =
    body.types(terms)

  private def setter(name: String): String =
    "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1)
}
