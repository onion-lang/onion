package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

final class MethodLookupSupport(
  private val typing: Typing,
  private val bodyContext: TypingBodyContext
) {

  def findMethod(node: AST.Node, target: ObjectType, name: String): Method =
    findMethod(node, target, name, Array.empty)

  def findMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Method = {
    val methods = MethodResolution.findMethods(target, name, params, bodyContext.table)
    if (methods.isEmpty) {
      bodyContext.report(METHOD_NOT_FOUND, node, target, name, params.map(_.`type`))
      null
    } else {
      methods(0)
    }
  }

  def tryFindMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Either[Boolean, Method] = {
    val methods = MethodResolution.findMethods(target, name, params, bodyContext.table)
    if (methods.nonEmpty) {
      if (methods.length > 1) {
        bodyContext.report(
          AMBIGUOUS_METHOD,
          node,
          Array[AnyRef](methods(0).affiliation, name, methods(0).arguments),
          Array[AnyRef](methods(1).affiliation, name, methods(1).arguments)
        )
        Left(false)
      } else if (!MemberAccess.isMemberAccessible(methods(0), bodyContext.definition)) {
        bodyContext.report(METHOD_NOT_ACCESSIBLE, node, methods(0).affiliation, name, methods(0).arguments, bodyContext.definition)
        Left(false)
      } else {
        Right(methods(0))
      }
    } else {
      Left(true)
    }
  }

  def types(terms: Array[Term]): Array[Type] =
    terms.map(_.`type`)

  def typeNames(types: Array[Type]): Array[String] =
    types.map(_.name)

  def addArgument(arg: AST.Argument, context: LocalContext): Type = {
    val name = arg.name
    val binding = context.lookupOnlyCurrentScope(name)
    if (binding != null) {
      bodyContext.report(DUPLICATE_LOCAL_VARIABLE, arg, name)
      null
    } else {
      typing.mapFrom(arg.typeRef, bodyContext.mapper) match {
        case Some(argType) =>
          context.add(name, argType)
          argType
        case None => null
      }
    }
  }
}
