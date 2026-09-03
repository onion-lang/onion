package onion.compiler.typing

import scala.util.boundary
import scala.util.boundary.break

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

final class StringInterpolationTyping(
  private val typing: Typing,
  private val bodyContext: TypingBodyContext,
  private val body: TypingBodyPass
) {

  // `StringBuilder.append` has a dozen overloads and every interpolated part resolved
  // them from scratch; the overload depends only on the part's type, so it is kept per
  // type for the pass. Same for the final `toString`.
  private val appendMemo = new java.util.HashMap[Type, Array[Method]]()
  private var sbToStringMemo: Array[Method] = null

  private def appendFor(sbType: ClassType, part: Term): Array[Method] = {
    val key = part.`type`
    val cached = appendMemo.get(key)
    if (cached != null) cached
    else {
      val found = sbType.findMethod("append", Array(part))
      appendMemo.put(key, found)
      found
    }
  }

  private def sbToString(sbType: ClassType): Array[Method] = {
    if (sbToStringMemo == null) sbToStringMemo = sbType.findMethod("toString", Array[Term]())
    sbToStringMemo
  }

  private lazy val cachedStringType: ClassType = bodyContext.load("java.lang.String")
  private lazy val cachedSbType: ClassType = bodyContext.load("java.lang.StringBuilder")
  private lazy val cachedNoArgConstructor: ConstructorRef = {
    val constructors = cachedSbType.findConstructor(Array[Term]())
    if (constructors.isEmpty) null else constructors(0)
  }

  def typeStringInterpolation(node: AST.StringInterpolation, context: LocalContext): Option[Term] = boundary {
    val typedExprs = node.expressions.map(e => typed(e, context).getOrElse(null))
    if (typedExprs.contains(null)) break(None)

    // Fixed for a pass: the two JDK types and StringBuilder's no-arg constructor were looked
    // up (the constructor through overload resolution) at every interpolated string.
    val stringType = cachedStringType
    val sbType = cachedSbType
    val noArgConstructor = cachedNoArgConstructor
    if (noArgConstructor == null) {
      bodyContext.report(CONSTRUCTOR_NOT_FOUND, node, sbType, Array[Type](), sbType.constructors)
      break(None)
    }

    val sb = new NewObject(noArgConstructor, Array[Term]())
    var result: Term = sb

    val parts = node.parts
    for (i <- parts.indices) {
      if (parts(i).nonEmpty) {
        val part = new StringValue(node.location, parts(i), stringType)
        val appendMethods = appendFor(sbType, part)
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
        val appendMethods = appendFor(sbType, expr)
        if (appendMethods.nonEmpty) {
          result = new Call(result, appendMethods(0), Array(expr))
        } else {
          expr.`type` match {
            case objectType: ObjectType =>
              val toStringMethods = objectType.findMethod("toString", Array[Term]())
              if (toStringMethods.nonEmpty) {
                val stringExpr = new Call(expr, toStringMethods(0), Array[Term]())
                val appendStringMethods = appendFor(sbType, stringExpr)
                if (appendStringMethods.nonEmpty) {
                  result = new Call(result, appendStringMethods(0), Array(stringExpr))
                }
              }
            case other =>
              bodyContext.report(INCOMPATIBLE_TYPE, node, bodyContext.rootClass, other)
              break(None)
          }
        }
      }
    }

    val toStringMethods = sbToString(sbType)
    if (toStringMethods.isEmpty) {
      bodyContext.report(METHOD_NOT_FOUND, node, sbType, "toString", Array[Type]())
      break(None)
    }
    Some(new Call(result, toStringMethods(0), Array[Term]()))
  }

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)
}
