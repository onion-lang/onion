package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

private[typing] final case class ResolvedMethodInvocation(
  methodSubst: scala.collection.immutable.Map[String, Type],
  expectedArgs: Array[Type]
)

private[compiler] final class MethodInvocationBuilderSupport(
  typing: Typing,
  calls: MethodCallTyping
) {
  def resolveInvocation(
    node: AST.Node,
    method: Method,
    params: Array[Term],
    typeArgs: List[AST.TypeNode],
    classSubst: scala.collection.immutable.Map[String, Type],
    expected: Type
  ): Option[ResolvedMethodInvocation] =
    calls.resolveMethodTypeArgs(node, method, params, typeArgs, classSubst, expected).map { methodSubst =>
      ResolvedMethodInvocation(methodSubst, TypeSubst.args(method, classSubst, methodSubst))
    }

  def buildResolvedCall(
    node: AST.Node,
    method: Method,
    params: Array[Term],
    typeArgs: List[AST.TypeNode],
    classSubst: scala.collection.immutable.Map[String, Type],
    expected: Type
  )(
    prepareParams: Array[Type] => Option[Array[Term]],
    buildRawCall: Array[Term] => Term
  ): Option[Term] =
    for {
      resolved <- resolveInvocation(node, method, params, typeArgs, classSubst, expected)
      finalParams <- prepareParams(resolved.expectedArgs)
    } yield castCall(buildRawCall(finalParams), method, classSubst, resolved.methodSubst)

  def castCall(
    call: Term,
    method: Method,
    classSubst: scala.collection.immutable.Map[String, Type],
    methodSubst: scala.collection.immutable.Map[String, Type]
  ): Term = {
    val castType = TypeSubst(method.returnType, classSubst, methodSubst)
    call match {
      case _: SafeCall | _: SafeFieldAccess =>
        // Safe navigation yields null when the target is null: keep the
        // nullable result type instead of casting back to the raw return
        // type (which unboxes primitives and NPEs on the null path)
        castType match {
          case _: NullableType | _: NullType => TypeSubst.withCast(call, castType)
          case bt: BasicType => call // SafeCall.type is already NullableType(bt)
          case other if other eq method.returnType => call
          case other => TypeSubst.withCast(call, NullableType.of(other))
        }
      case _ =>
        TypeSubst.withCast(call, castType)
    }
  }
}
