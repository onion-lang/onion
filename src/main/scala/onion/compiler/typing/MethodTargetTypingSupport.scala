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
      case nullable: NullableType =>
        bodyContext.report(NULLABLE_MEMBER_ACCESS, node, nullable.displayName)
        None
      case targetType =>
        resolve(node, node.name, target, targetType, isNullablePrimitive = false)
    }

  /** `?.` accepts a nullable receiver: the call is typed against the inner type. */
  def normalizeSafeMethodCallTarget(
    node: AST.SafeMethodCall,
    target: Term
  ): Option[ResolvedMethodTarget] = {
    val (targetType, isNullablePrimitive) = target.`type` match {
      case nullableType: NullableType => (nullableType.innerType, true)
      case other => (other, false)
    }
    resolve(node, node.name, target, targetType, isNullablePrimitive)
  }

  /** The `bind` the rewriter makes from a bare statement of a `do[M]` block: its closure
   *  parameter is the reserved `AST.DoStatementParameter`. */
  private def isDoStatementBind(node: AST.Node, name: String): Boolean =
    name == "bind" && (node match {
      case AST.MethodCall(_, _, _, List(AST.ClosureExpression(_, _, _, List(arg), _, _)), _) => arg.name == AST.DoStatementParameter
      case _ => false
    })

  /** The receiver a method can be looked up on: an object type as is, a primitive boxed, a wildcard through its upper bound. */
  private def resolve(node: AST.Node, name: String, target: Term, targetType: Type, isNullablePrimitive: Boolean): Option[ResolvedMethodTarget] =
    targetType match {
      case objType: ObjectType =>
        Some(ResolvedMethodTarget(target, objType))
      case basicType: BasicType =>
        if (basicType == BasicType.VOID) {
          if (isDoStatementBind(node, name))
            bodyContext.report(CANNOT_CALL_METHOD_ON_PRIMITIVE, node, basicType, name, SemanticErrorReporter.DoStatementVoidHint)
          else
            bodyContext.report(CANNOT_CALL_METHOD_ON_PRIMITIVE, node, basicType, name)
          None
        } else if (isNullablePrimitive) {
          // A nullable primitive (e.g. Int?) is already a boxed value at runtime,
          // so retype the target to the boxed class instead of boxing it again
          // (boxing a NullableType-typed term crashes: "not a boxable type").
          val boxedType = Boxing.boxedType(bodyContext.table, basicType).asInstanceOf[ObjectType]
          Some(ResolvedMethodTarget(new AsInstanceOf(target, boxedType), boxedType))
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
