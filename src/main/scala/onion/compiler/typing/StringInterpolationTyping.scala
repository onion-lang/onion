package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

final class StringInterpolationTyping(private val typing: Typing, private val body: TypingBodyPass) {
  import typing.*

  def typeStringInterpolation(node: AST.StringInterpolation, context: LocalContext): Option[Term] = {
    val typedExprs = node.expressions.map(e => typed(e, context).getOrElse(null))
    if (typedExprs.contains(null)) return None

    val stringType = load("java.lang.String")
    val sbType = load("java.lang.StringBuilder")

    val constructors = sbType.findConstructor(Array[Term]())
    if (constructors.isEmpty) {
      report(CONSTRUCTOR_NOT_FOUND, node, sbType, Array[Type]())
      return None
    }
    val noArgConstructor = constructors(0)

    val sb = new NewObject(noArgConstructor, Array[Term]())
    var result: Term = sb

    val parts = node.parts
    for (i <- parts.indices) {
      if (parts(i).nonEmpty) {
        val part = new StringValue(node.location, parts(i), stringType)
        val appendMethods = sbType.findMethod("append", Array(part))
        if (appendMethods.nonEmpty) {
          result = new Call(result, appendMethods(0), Array(part))
        }
      }

      if (i < typedExprs.length) {
        val expr = typedExprs(i)
        val appendMethods = sbType.findMethod("append", Array(expr))
        if (appendMethods.nonEmpty) {
          result = new Call(result, appendMethods(0), Array(expr))
        } else {
          val toStringMethods = expr.`type`.asInstanceOf[ObjectType].findMethod("toString", Array[Term]())
          if (toStringMethods.nonEmpty) {
            val stringExpr = new Call(expr, toStringMethods(0), Array[Term]())
            val appendStringMethods = sbType.findMethod("append", Array(stringExpr))
            if (appendStringMethods.nonEmpty) {
              result = new Call(result, appendStringMethods(0), Array(stringExpr))
            }
          }
        }
      }
    }

    val toStringMethods = sbType.findMethod("toString", Array[Term]())
    if (toStringMethods.isEmpty) {
      report(METHOD_NOT_FOUND, node, sbType, "toString", Array[Type]())
      return None
    }
    Some(new Call(result, toStringMethods(0), Array[Term]()))
  }

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)
}
