package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

private[compiler] final class CallableValueCallSupport(
  bodyContext: TypingBodyContext,
  calls: MethodCallTyping
) {
  private def hasCallMethod(tp: Type): Boolean = tp match {
    case objType: ObjectType => objType.methods("call").nonEmpty
    case _ => false
  }

  def resolveCallableValue(
    node: AST.UnqualifiedMethodCall,
    params: Array[Term],
    context: LocalContext,
    expected: Type
  ): MethodFallbackLookup[Term] = {
    if (node.typeArgs.nonEmpty) return MethodFallbackLookup.NotFound

    def callOnTarget(target: Term, targetType: ObjectType): MethodFallbackLookup[Term] = {
      if (targetType.methods("call").isEmpty) return MethodFallbackLookup.NotFound
      val callNode = new AST.MethodCall(node.location, new AST.Id(node.location, node.name), "call", node.args, Nil)
      calls.typeMethodCallOnObject(callNode, target, targetType, params, context, expected) match {
        case Some(term) => MethodFallbackLookup.Found(term)
        case None => MethodFallbackLookup.Error
      }
    }

    // A nullable local/field whose inner type is a genuinely callable value
    // (e.g. `f: Function1[A, B]?`) is dereferenced by `f(x)` exactly like the
    // equivalent explicit `f.call(x)`, which already reports NULLABLE_MEMBER_ACCESS
    // via MethodTargetTypingSupport.normalizeMethodCallTarget -- mirror that here
    // instead of silently falling through to a misleading "method not found".
    def reportNullable(nullable: NullableType): MethodFallbackLookup[Term] = {
      bodyContext.report(NULLABLE_MEMBER_ACCESS, node, nullable.displayName)
      MethodFallbackLookup.Error
    }

    val local = context.lookup(node.name)
    if (local != null) {
      context.recordUsage(node.name)
      local.tp match
        case targetType: ObjectType => return callOnTarget(new RefLocal(local), targetType)
        case nullable: NullableType if hasCallMethod(nullable.innerType) => return reportNullable(nullable)
        case _ => return MethodFallbackLookup.NotFound
    }

    if (!context.isStatic) {
      val owner = bodyContext.definition
      val field = MemberAccess.findField(owner, node.name)
      if (field != null && MemberAccess.isMemberAccessible(field, owner)) {
        field.`type` match
          case targetType: ObjectType =>
            return callOnTarget(new RefField(new This(owner), field), targetType)
          case nullable: NullableType if hasCallMethod(nullable.innerType) => return reportNullable(nullable)
          case _ =>
      }
    }

    MethodFallbackLookup.NotFound
  }
}
