package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext
import onion.compiler.toolbox.Boxing

private[typing] sealed trait ResolvedMemberSelection
private[typing] case object ResolvedArrayLengthSelection extends ResolvedMemberSelection
private[typing] final case class ResolvedFieldSelection(field: FieldRef) extends ResolvedMemberSelection
private[typing] final case class ResolvedGetterSelection(method: Method) extends ResolvedMemberSelection

private[typing] final case class ResolvedMemberSelectionTarget(
  term: Term,
  targetType: ObjectType
)

private[compiler] final class MemberSelectionResolutionSupport(
  private val typing: Typing,
  private val bodyContext: TypingBodyContext,
  private val calls: MethodCallTyping
) {
  def normalizeMemberSelectionTarget(
    node: AST.MemberSelection,
    target: Term
  ): Option[ResolvedMemberSelectionTarget] =
    target.`type` match {
      // A bare [T] ranges over nullable types: plain member access needs a
      // null check first (?. remains available)
      case tv: TypeVariableType if tv.nullability == Nullability.Nullable =>
        bodyContext.report(TYPE_PARAMETER_MAY_BE_NULL, node, tv.displayName)
        None
      case _ =>
        normalizeTarget(node.target, target, target.`type`)
    }

  def normalizeSafeMemberSelectionTarget(
    node: AST.SafeMemberSelection,
    target: Term
  ): Option[ResolvedMemberSelectionTarget] =
    normalizeTarget(
      node.target,
      target,
      target.`type` match {
        case nullableType: NullableType => nullableType.innerType
        case other => other
      }
    )

  def resolveMemberSelection(
    node: AST.Node,
    targetType: ObjectType,
    name: String
  ): Option[ResolvedMemberSelection] = {
    if (targetType.isArrayType) {
      if (name == MethodNames.LENGTH || name == MethodNames.SIZE) Some(ResolvedArrayLengthSelection)
      else {
        // An array has only `length`/`size`; any other member is undefined. Report
        // it (like the non-array branch below) instead of returning a silent None,
        // which let the selection type-check as a bad term and miscompile into
        // invalid bytecode (a VerifyError). (found by the mutation fuzzer)
        bodyContext.report(FIELD_NOT_FOUND, node, targetType, name)
        None
      }
    } else {
      val field = MemberAccess.findField(targetType, name)
      if (field != null && MemberAccess.isMemberAccessible(field, bodyContext.definition)) {
        Some(ResolvedFieldSelection(field))
      } else {
        val methodNames = Array(name, calls.getter(name), calls.getterBoolean(name))
        var methodIndex = 0
        while (methodIndex < methodNames.length) {
          val methodName = methodNames(methodIndex)
          calls.tryFindMethod(node, targetType, methodName, Array.empty) match {
            case Right(method) =>
              return Some(ResolvedGetterSelection(method))
            case Left(false) =>
              return None
            case Left(true) =>
          }
          methodIndex += 1
        }

        if (field == null) bodyContext.report(FIELD_NOT_FOUND, node, targetType, name)
        else bodyContext.report(FIELD_NOT_ACCESSIBLE, node, targetType, name, bodyContext.definition)
        None
      }
    }
  }

  private def normalizeTarget(
    targetNode: AST.Expression,
    target: Term,
    targetType: Type
  ): Option[ResolvedMemberSelectionTarget] =
    targetType match {
      case nullType if nullType.isNullType =>
        bodyContext.report(INCOMPATIBLE_TYPE, targetNode, bodyContext.rootClass, target.`type`)
        None
      case basicType: BasicType =>
        if (basicType == BasicType.VOID) {
          bodyContext.report(INCOMPATIBLE_TYPE, targetNode, bodyContext.rootClass, basicType)
          None
        } else {
          val boxed = Boxing.boxing(bodyContext.table, target)
          val boxedType = boxed.`type`.asInstanceOf[ObjectType]
          if (MemberAccess.ensureTypeAccessible(typing, targetNode, boxedType, bodyContext.definition))
            Some(ResolvedMemberSelectionTarget(boxed, boxedType))
          else
            None
        }
      case objectType: ObjectType =>
        if (MemberAccess.ensureTypeAccessible(typing, targetNode, objectType, bodyContext.definition))
          Some(ResolvedMemberSelectionTarget(target, objectType))
        else
          None
      case nullable: NullableType =>
        // A nullable value (`String?`) cannot be dereferenced directly. Report a
        // null-safety error pointing at `?.`/`?:`/`!!`/a null check, mirroring the
        // method-call path — not the misleading INCOMPATIBLE_TYPE ("Object expected")
        // the generic fallback below used to produce for field access.
        bodyContext.report(NULLABLE_MEMBER_ACCESS, targetNode, nullable.displayName)
        None
      case _ =>
        bodyContext.report(INCOMPATIBLE_TYPE, targetNode, bodyContext.rootClass, targetType)
        None
    }
}
