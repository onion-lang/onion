package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

private[compiler] final class MethodCallReportingSupport(
  bodyContext: TypingBodyContext,
  calls: MethodCallTyping
) {
  def reportMethodNotFound(
    node: AST.Node,
    targetType: AnyRef,
    name: String,
    argTypes: Array[Type]
  ): Unit =
    // A named argument whose name matches no parameter of any candidate eliminates
    // every candidate during filtering, so the call arrives here as "not found" and
    // the dedicated UNKNOWN_PARAMETER_NAME check downstream is never reached
    // (issue #426). The typo is the real problem, so say so: `f(x = 1, nope = 2)`
    // should name `nope`, not report that `T.f()` does not exist.
    unknownNamedArgument(node, targetType, name) match {
      case Some(bad) => bodyContext.report(UNKNOWN_PARAMETER_NAME, bad, bad.name)
      case None      => bodyContext.report(METHOD_NOT_FOUND, node, targetType, name, argTypes)
    }

  /**
   * The first named argument of `node` whose name is not a parameter of any method
   * called `name` on `targetType`. `None` when the call has no named arguments, when
   * the target's methods cannot be enumerated, or when every name is legitimate (in
   * which case the call really is not-found for some other reason).
   */
  private def unknownNamedArgument(
    node: AST.Node,
    targetType: AnyRef,
    name: String
  ): Option[AST.NamedArgument] = {
    val args: List[AST.Expression] = node match {
      case c: AST.MethodCall            => c.args
      case c: AST.UnqualifiedMethodCall => c.args
      case c: AST.StaticMethodCall      => c.args
      case _                            => Nil
    }
    val named = args.collect { case n: AST.NamedArgument => n }
    if (named.isEmpty) return None
    val candidates = targetType match {
      case ot: ObjectType => ot.methods(name)
      case _              => Array.empty[Method]
    }
    if (candidates.isEmpty) return None
    val known = candidates.flatMap(_.argumentsWithDefaults.map(_.name)).toSet
    named.find(n => !known.contains(n.name))
  }

  def reportAmbiguousMethods(
    node: AST.Node,
    name: String,
    methods: Array[Method]
  ): Unit =
    reportAmbiguousMethod(node, methods(0), methods(1), name)

  def reportAmbiguousMethod(
    node: AST.Node,
    first: Method,
    second: Method,
    name: String = null
  ): Unit = {
    val methodName = if (name != null) name else first.name
    reportAmbiguousSignature(node, first.affiliation, methodName, first.arguments, second.affiliation, methodName, second.arguments)
  }

  def reportAmbiguousSignature(
    node: AST.Node,
    firstAffiliation: AnyRef,
    firstName: String,
    firstArguments: Array[Type],
    secondAffiliation: AnyRef,
    secondName: String,
    secondArguments: Array[Type]
  ): Unit = {
    bodyContext.report(
      AMBIGUOUS_METHOD,
      node,
      Array[AnyRef](firstAffiliation, firstName, firstArguments),
      Array[AnyRef](secondAffiliation, secondName, secondArguments)
    )
  }

  def reportIllegalMethodCall(
    node: AST.Node,
    method: Method,
    name: String
  ): Unit =
    bodyContext.report(ILLEGAL_METHOD_CALL, node, method.affiliation, name, method.arguments)
}
