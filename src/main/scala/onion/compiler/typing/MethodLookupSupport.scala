package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

final class MethodLookupSupport(private val typing: Typing) {
  import typing.*

  def findMethod(node: AST.Node, target: ObjectType, name: String): Method =
    findMethod(node, target, name, Array.empty)

  def findMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Method = {
    val methods = MethodResolution.findMethods(target, name, params, table_)
    if (methods.isEmpty) {
      report(METHOD_NOT_FOUND, node, target, name, params.map(_.`type`))
      null
    } else {
      methods(0)
    }
  }

  def tryFindMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Either[Continuable, Method] = {
    val methods = MethodResolution.findMethods(target, name, params, table_)
    if (methods.nonEmpty) {
      if (methods.length > 1) {
        report(
          AMBIGUOUS_METHOD,
          node,
          Array[AnyRef](methods(0).affiliation, name, methods(0).arguments),
          Array[AnyRef](methods(1).affiliation, name, methods(1).arguments)
        )
        Left(false)
      } else if (!MemberAccess.isMemberAccessible(methods(0), definition_)) {
        report(METHOD_NOT_ACCESSIBLE, node, methods(0).affiliation, name, methods(0).arguments, definition_)
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
      report(DUPLICATE_LOCAL_VARIABLE, arg, name)
      null
    } else {
      val argType = mapFrom(arg.typeRef, mapper_)
      if (argType == null) null
      else {
        context.add(name, argType)
        argType
      }
    }
  }
}
