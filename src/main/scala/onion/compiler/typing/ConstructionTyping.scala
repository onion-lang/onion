package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

final class ConstructionTyping(private val typing: Typing, private val body: TypingBodyPass) {
  import typing.*

  def typeIndexing(node: AST.Indexing, context: LocalContext): Option[Term] = {
    val target = typed(node.lhs, context).getOrElse(null)
    val index = typed(node.rhs, context).getOrElse(null)
    if (target == null || index == null) return None

    if (target.isArrayType) {
      if (!(index.isBasicType && index.`type`.asInstanceOf[BasicType].isInteger)) {
        report(INCOMPATIBLE_TYPE, node, BasicType.INT, index.`type`)
        return None
      }
      Some(new RefArray(target, index))
    } else if (target.isBasicType) {
      report(INCOMPATIBLE_TYPE, node.lhs, rootClass, target.`type`)
      None
    } else {
      val params = Array(index)
      tryFindMethod(node, target.`type`.asInstanceOf[ObjectType], "get", Array[Term](index)) match {
        case Left(_) =>
          report(METHOD_NOT_FOUND, node, target.`type`, "get", types(params))
          None
        case Right(method) =>
          Some(new Call(target, method, params))
      }
    }
  }

  def typeNewArray(node: AST.NewArray, context: LocalContext): Option[Term] = {
    val typeRef = mapFrom(node.typeRef, mapper_)
    val parameters = typedTerms(node.args.toArray, context)
    if (typeRef == null || parameters == null) return None
    val resultType = loadArray(typeRef, parameters.length)
    Some(new NewArray(resultType, parameters))
  }

  def typeNewObject(node: AST.NewObject, context: LocalContext): Option[Term] = {
    val typeRef = mapFrom(node.typeRef).asInstanceOf[ClassType]
    val parameters = typedTerms(node.args.toArray, context)
    if (parameters == null || typeRef == null) return None
    val constructors = typeRef.findConstructor(parameters)
    if (constructors.length == 0) {
      report(CONSTRUCTOR_NOT_FOUND, node, typeRef, types(parameters))
      None
    } else if (constructors.length > 1) {
      report(
        AMBIGUOUS_CONSTRUCTOR,
        node,
        Array[AnyRef](constructors(0).affiliation, constructors(0).getArgs),
        Array[AnyRef](constructors(1).affiliation, constructors(1).getArgs)
      )
      None
    } else {
      typeRef match {
        case applied: TypedAST.AppliedClassType =>
          val appliedCtor = new TypedAST.ConstructorRef {
            def modifier: Int = constructors(0).modifier
            def affiliation: TypedAST.ClassType = applied
            def name: String = constructors(0).name
            def getArgs: Array[TypedAST.Type] = constructors(0).getArgs
          }
          Some(new NewObject(appliedCtor, parameters))
        case _ =>
          Some(new NewObject(constructors(0), parameters))
      }
    }
  }

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)

  private def typedTerms(nodes: Array[AST.Expression], context: LocalContext): Array[Term] =
    body.typedTerms(nodes, context)

  private def tryFindMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Either[Boolean, Method] =
    body.tryFindMethod(node, target, name, params)

  private def types(terms: Array[Term]): Array[Type] =
    body.types(terms)
}
