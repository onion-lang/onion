package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Kind.*

final class ExpressionFormTyping(private val typing: Typing, private val body: TypingBodyPass) {
  import typing.*
  private val constructionTyping = new ConstructionTyping(typing, body)
  private val stringInterpolationTyping = new StringInterpolationTyping(typing, body)

  def typeIndexing(node: AST.Indexing, context: LocalContext): Option[Term] =
    constructionTyping.typeIndexing(node, context)

  def typeNewArray(node: AST.NewArray, context: LocalContext): Option[Term] =
    constructionTyping.typeNewArray(node, context)

  def typeNewArrayWithValues(node: AST.NewArrayWithValues, context: LocalContext): Option[Term] =
    constructionTyping.typeNewArrayWithValues(node, context)

  def typeNewObject(node: AST.NewObject, context: LocalContext): Option[Term] =
    constructionTyping.typeNewObject(node, context)

  def typeStringInterpolation(node: AST.StringInterpolation, context: LocalContext): Option[Term] =
    stringInterpolationTyping.typeStringInterpolation(node, context)

  def typeElvis(node: AST.Elvis, context: LocalContext): Option[Term] =
    for {
      left <- typed(node.lhs, context)
      right <- typed(node.rhs, context)
      result <- if (left.isBasicType || right.isBasicType || !TypeRules.isAssignable(left.`type`, right.`type`)) {
        report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
        None
      } else Some(new BinaryTerm(ELVIS, left.`type`, left, right))
    } yield result

  def typeCast(node: AST.Cast, context: LocalContext): Option[Term] =
    typed(node.src, context).flatMap { term =>
      Option(mapFrom(node.to, mapper_)).flatMap { destination =>
        if (term.`type`.isBasicType && !destination.isBasicType) {
          report(INCOMPATIBLE_TYPE, node, destination, term.`type`)
          None
        } else {
          Some(new AsInstanceOf(term, destination))
        }
      }
    }

  def typeIsInstance(node: AST.IsInstance, context: LocalContext): Option[Term] =
    for {
      target <- typed(node.target, context)
      destinationType <- Option(mapFrom(node.typeRef, mapper_))
    } yield new InstanceOf(target, destinationType)

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)
}
