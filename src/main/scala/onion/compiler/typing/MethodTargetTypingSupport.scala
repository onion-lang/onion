package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext
import onion.compiler.toolbox.Boxing

private[typing] final case class ResolvedMethodTarget(
  term: Term,
  targetType: ObjectType
)

private[compiler] final class MethodTargetTypingSupport(private val bodyContext: TypingBodyContext) {

  def normalizeMethodCallTarget(
    node: AST.MethodCall,
    target: Term
  ): Option[ResolvedMethodTarget] =
    target.`type` match {
      // A bare [T] ranges over nullable types, so its values can't be
      // dereferenced until null is excluded (Platform variables from Java
      // classes stay permissive)
      case tv: TypeVariableType if tv.nullability == Nullability.Nullable =>
        bodyContext.report(TYPE_PARAMETER_MAY_BE_NULL, node, tv.displayName)
        None
      case targetType: ObjectType =>
        Some(ResolvedMethodTarget(target, targetType))
      case basicType: BasicType =>
        if (basicType == BasicType.VOID) {
          bodyContext.report(CANNOT_CALL_METHOD_ON_PRIMITIVE, node, basicType, node.name)
          None
        } else {
          val boxed = Boxing.boxing(bodyContext.table, target)
          Some(ResolvedMethodTarget(boxed, boxed.`type`.asInstanceOf[ObjectType]))
        }
      case wildcardType: WildcardType =>
        wildcardType.upperBound match {
          case objType: ObjectType =>
            Some(ResolvedMethodTarget(new AsInstanceOf(target, objType), objType))
          case _ =>
            bodyContext.report(INVALID_METHOD_CALL_TARGET, node, target.`type`)
            None
        }
      case _ =>
        bodyContext.report(INVALID_METHOD_CALL_TARGET, node, target.`type`)
        None
    }

  def normalizeSafeMethodCallTarget(
    node: AST.SafeMethodCall,
    target: Term
  ): Option[ResolvedMethodTarget] = {
    val targetType = target.`type` match {
      case nullableType: NullableType => nullableType.innerType
      case other => other
    }

    targetType match {
      case objType: ObjectType =>
        Some(ResolvedMethodTarget(target, objType))
      case basicType: BasicType =>
        if (basicType == BasicType.VOID) {
          bodyContext.report(CANNOT_CALL_METHOD_ON_PRIMITIVE, node, basicType, node.name)
          None
        } else {
          val boxed = Boxing.boxing(bodyContext.table, target)
          Some(ResolvedMethodTarget(boxed, boxed.`type`.asInstanceOf[ObjectType]))
        }
      case wildcardType: WildcardType =>
        wildcardType.upperBound match {
          case objType: ObjectType =>
            Some(ResolvedMethodTarget(new AsInstanceOf(target, objType), objType))
          case _ =>
            bodyContext.report(INVALID_METHOD_CALL_TARGET, node, target.`type`)
            None
        }
      case _ =>
        bodyContext.report(INVALID_METHOD_CALL_TARGET, node, target.`type`)
        None
    }
  }
}
