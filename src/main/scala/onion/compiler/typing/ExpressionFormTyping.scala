package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Constants.*

final class ExpressionFormTyping(private val typing: Typing, private val body: TypingBodyPass) {
  import typing.*
  private val constructionTyping = new ConstructionTyping(typing, body)
  private val stringInterpolationTyping = new StringInterpolationTyping(typing, body)

  def typeIndexing(node: AST.Indexing, context: LocalContext): Option[Term] =
    constructionTyping.typeIndexing(node, context)

  def typeNewArray(node: AST.NewArray, context: LocalContext): Option[Term] =
    constructionTyping.typeNewArray(node, context)

  def typeNewObject(node: AST.NewObject, context: LocalContext): Option[Term] =
    constructionTyping.typeNewObject(node, context)

  def typeStringInterpolation(node: AST.StringInterpolation, context: LocalContext): Option[Term] =
    stringInterpolationTyping.typeStringInterpolation(node, context)

  def typeElvis(node: AST.Elvis, context: LocalContext): Option[Term] = {
    val left = typed(node.lhs, context).getOrElse(null)
    val right = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return None
    if (left.isBasicType || right.isBasicType || !TypeRules.isAssignable(left.`type`, right.`type`)) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
      None
    } else {
      Some(new BinaryTerm(ELVIS, left.`type`, left, right))
    }
  }

  def typeCast(node: AST.Cast, context: LocalContext): Option[Term] = {
    val term = typed(node.src, context).getOrElse(null)
    if (term == null) None
    else {
      val destination = mapFrom(node.to, mapper_)
      if (destination == null) None
      else Some(new AsInstanceOf(term, destination))
    }
  }

  def typeIsInstance(node: AST.IsInstance, context: LocalContext): Option[Term] = {
    val target = typed(node.target, context).getOrElse(null)
    val destinationType = mapFrom(node.typeRef, mapper_)
    if (target == null || destinationType == null) None
    else Some(new InstanceOf(target, destinationType))
  }

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)
}
