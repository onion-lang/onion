package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

final class StringInterpolationTyping(
  private val typing: Typing,
  private val bodyContext: TypingBodyContext,
  private val body: TypingBodyPass
) {

  def typeStringInterpolation(node: AST.StringInterpolation, context: LocalContext): Option[Term] = {
    val typedExprs = node.expressions.map(e => typed(e, context).getOrElse(null))
    if (typedExprs.contains(null)) return None

    val stringType = bodyContext.load("java.lang.String")
    val sbType = bodyContext.load("java.lang.StringBuilder")

    val constructors = sbType.findConstructor(Array[Term]())
    if (constructors.isEmpty) {
      bodyContext.report(CONSTRUCTOR_NOT_FOUND, node, sbType, Array[Type](), sbType.constructors)
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
        // A null literal would otherwise match append(char[]) and NPE at runtime;
        // cast it to Object so append(Object) renders it as "null"
        val expr =
          if (typedExprs(i).`type`.isNullType) new AsInstanceOf(typedExprs(i), bodyContext.rootClass)
          else typedExprs(i)
        val appendMethods = sbType.findMethod("append", Array(expr))
        if (appendMethods.nonEmpty) {
          result = new Call(result, appendMethods(0), Array(expr))
        } else {
          expr.`type` match {
            case objectType: ObjectType =>
              val toStringMethods = objectType.findMethod("toString", Array[Term]())
              if (toStringMethods.nonEmpty) {
                val stringExpr = new Call(expr, toStringMethods(0), Array[Term]())
                val appendStringMethods = sbType.findMethod("append", Array(stringExpr))
                if (appendStringMethods.nonEmpty) {
                  result = new Call(result, appendStringMethods(0), Array(stringExpr))
                }
              }
            case other =>
              bodyContext.report(INCOMPATIBLE_TYPE, node, bodyContext.rootClass, other)
              return None
          }
        }
      }
    }

    val toStringMethods = sbType.findMethod("toString", Array[Term]())
    if (toStringMethods.isEmpty) {
      bodyContext.report(METHOD_NOT_FOUND, node, sbType, "toString", Array[Type]())
      return None
    }
    Some(new Call(result, toStringMethods(0), Array[Term]()))
  }

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)
}
