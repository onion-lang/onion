package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext
import onion.compiler.toolbox.Boxing

final class AssignmentTyping(
  private val typing: Typing,
  private val bodyContext: TypingBodyContext,
  private val body: TypingBodyPass
) {

  def processLocalAssign(node: AST.Assignment, context: LocalContext): Term = {
    var value: Term = typed(node.rhs, context).getOrElse(null)
    if (value == null) return null
    val id = node.lhs.asInstanceOf[AST.Id]
    val bind = context.lookup(id.name)
    if (bind == null) {
      bodyContext.report(VARIABLE_NOT_FOUND, id, id.name, context.allNames.toArray)
      return null
    }
    if (!bind.isMutable) {
      bodyContext.report(CANNOT_ASSIGN_TO_VAL, id, id.name)
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
    if (target.isBasicType || target.isNullType) {
      bodyContext.report(INCOMPATIBLE_TYPE, indexing.lhs, bodyContext.rootClass, target.`type`)
      return null
    }
    if (target.isArrayType) {
      val targetType = target.`type`.asInstanceOf[ArrayType]
      // Try to unbox Integer to int for array index
      if (!index.isBasicType) {
        Boxing.unboxedType(bodyContext.table, index.`type`) match {
          case Some(bt) if bt.isInteger => index = Boxing.unboxing(bodyContext.table, index, bt)
          case _ => // will fail below
        }
      }
      if (!(index.isBasicType && index.`type`.asInstanceOf[BasicType].isInteger)) {
        bodyContext.report(INCOMPATIBLE_TYPE, indexing.rhs, BasicType.INT, index.`type`)
        return null
      }
      if (value.`type`.isBottomType) return value
      value = processAssignable(node.rhs, targetType.base, value)
      if (value == null) return null
      new SetArray(target, index, value)
    } else {
      target.`type` match {
        case objType: ObjectType =>
          val params = Array[Term](index, value)
          tryFindMethod(node, objType, "set", Array[Term](index, value)) match {
            case Left(_) =>
              bodyContext.report(METHOD_NOT_FOUND, node, target.`type`, "set", types(params))
              if (value.`type`.isBottomType) value else null
            case Right(method) =>
              if (value.`type`.isBottomType) value else new Call(target, method, params)
          }
        case other =>
          // e.g. assigning through a nullable receiver: unwrap it first
          bodyContext.report(INVALID_METHOD_CALL_TARGET, indexing.lhs, other)
          null
      }
    }
  }

  def processMemberAssign(node: AST.Assignment, context: LocalContext): Term = {
    node match {
      case AST.Assignment(_, selection@AST.MemberSelection(_, _, _), expression) =>
        val contextClass = bodyContext.definition
        val target = typed(selection.target, context).getOrElse(null)
        if (target == null) return null
        if (target.`type`.isBasicType || target.`type`.isNullType) {
          bodyContext.report(INCOMPATIBLE_TYPE, selection.target, bodyContext.rootClass, target.`type`)
          return null
        }
        val targetType = target.`type` match {
          case objType: ObjectType => objType
          case other =>
            // e.g. assigning through a nullable receiver: unwrap it first
            bodyContext.report(INVALID_METHOD_CALL_TARGET, selection.target, other)
            return null
        }
        if (!MemberAccess.ensureTypeAccessible(typing, selection, targetType, contextClass)) return null
        val name = selection.name
        val field: FieldRef = MemberAccess.findField(targetType, name)
        val value: Term = typed(expression, context).getOrElse(null)
        if (value == null) return null
        if (field != null && MemberAccess.isMemberAccessible(field, bodyContext.definition)) {
          if (Modifier.isFinal(field.modifier) && (context.constructor == null || !target.isInstanceOf[This])) {
            bodyContext.report(CANNOT_ASSIGN_TO_VAL, selection, field.name)
            return null
          }
          val classSubst = TypeSubstitution.hierarchySubstitution(target.`type`, field.affiliation)
          // Keep unsubstituted type variables as T so diagnostics (W0012)
          // name the parameter rather than its bound
          val expected = TypeSubstitution.substituteType(field.`type`, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false)
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
        bodyContext.report(LVALUE_REQUIRED, node)
        null
    }
  }

  def typeAssignment(node: AST.Assignment, context: LocalContext): Option[Term] =
    node.lhs match {
      case id: AST.Id =>
        // A bare name that is an instance field assigns through `this`
        // (implicit field access); otherwise it's a local assignment.
        if (context.lookup(id.name) == null && isImplicitInstanceField(id.name, context)) {
          val rewritten = AST.Assignment(node.location,
            AST.MemberSelection(node.location, AST.CurrentInstance(node.location), id.name), node.rhs)
          Option(processMemberAssign(rewritten, context))
        } else if (context.lookup(id.name) == null) {
          resolveStaticFieldAssign(id, node, context).orElse(Option(processLocalAssign(node, context)))
        } else {
          Option(processLocalAssign(node, context))
        }
      case _: AST.Indexing =>
        Option(processArrayAssign(node, context))
      case _: AST.MemberSelection =>
        Option(processMemberAssign(node, context))
      case _: AST.StaticMemberSelection =>
        Option(processStaticFieldAssign(node, context))
      case _ =>
        // e.g. (null = expr): not an assignable target; report instead of
        // silently dropping (a silent None leaves zero errors and a null
        // condition reaching codegen)
        bodyContext.report(LVALUE_REQUIRED, node)
        None
    }

  /** Whether a bare name denotes a non-static field of the current class reachable through `this`. */
  private def isImplicitInstanceField(name: String, context: LocalContext): Boolean = {
    if (context.isStatic) return false
    val field = bodyContext.definition.findField(name)
    field != null && (field.modifier & AST.M_STATIC) == 0
  }

  /**
   * A bare-name assignment may target a static field of the current class or a
   * top-level val/var (a static field on the synthetic top-level class). Emit a
   * SetStaticField so top-level functions, main, and other classes can reassign
   * top-level vars. None when no such static field exists (falls back to local).
   */
  private def resolveStaticFieldAssign(id: AST.Id, node: AST.Assignment, context: LocalContext): Option[Term] = {
    val ownerField: Option[(ClassType, FieldRef)] =
      Option(bodyContext.definition.field(id.name)).map(f => (bodyContext.definition, f))
        .orElse(bodyContext.topLevelClass.flatMap(tc => Option(tc.field(id.name)).map(f => (tc, f))))
    ownerField match {
      case Some((owner, field)) if (field.modifier & AST.M_STATIC) != 0 =>
        val value = typed(node.rhs, context).getOrElse(null)
        if (value == null) None
        else if (value.`type`.isBottomType) Some(value)
        else {
          val term = processAssignable(node.rhs, field.`type`, value)
          if (term == null) None else Some(new SetStaticField(owner, field, term))
        }
      case _ => None
    }
  }

  def processStaticFieldAssign(node: AST.Assignment, context: LocalContext): Term = {
    node.lhs match {
      case selection: AST.StaticMemberSelection =>
        typing.mapFrom(selection.typeRef) match {
          case Some(typeRef: ClassType) =>
            val field = MemberAccess.findField(typeRef, selection.name)
            if (field == null) {
              bodyContext.report(FIELD_NOT_FOUND, selection, typeRef, selection.name)
              return null
            }
            if (!Modifier.isStatic(field.modifier)) {
              bodyContext.report(FIELD_NOT_FOUND, selection, typeRef, selection.name)
              return null
            }
            if (!MemberAccess.isMemberAccessible(field, bodyContext.definition)) {
              bodyContext.report(FIELD_NOT_ACCESSIBLE, selection, typeRef, selection.name, bodyContext.definition)
              return null
            }
            if (Modifier.isFinal(field.modifier)) {
              bodyContext.report(CANNOT_ASSIGN_TO_VAL, selection, field.name)
              return null
            }
            val value = typed(node.rhs, context).getOrElse(null)
            if (value == null) return null
            if (value.`type`.isBottomType) return value
            val term = processAssignable(node.rhs, field.`type`, value)
            if (term == null) return null
            new SetStaticField(typeRef, field, term)
          case Some(other) =>
            bodyContext.report(INCOMPATIBLE_TYPE, selection, bodyContext.rootClass, other)
            null
          case None => null
        }
      case _ =>
        bodyContext.report(LVALUE_REQUIRED, node)
        null
    }
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
