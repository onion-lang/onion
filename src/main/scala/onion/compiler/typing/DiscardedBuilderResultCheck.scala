package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

/**
 * W0019: an expression statement that calls an instance method of a known-immutable Onion
 * library class and discards a result of that same class. Such a call is a builder step whose
 * new value is thrown away, so it does nothing.
 *
 * Deliberately an allowlist: classes like `Future` (`onSuccess`) and `Server.Instance`
 * (`handle`) return `this` after mutating, and are normally called as statements.
 */
private[typing] object DiscardedBuilderResultCheck {
  private val ImmutableBuilders: Set[String] = Set(
    "onion.Http.Request", "onion.Server.Response", "onion.Shape", "onion.Lossless",
    "onion.Defect", "onion.Origin", "onion.llm.Claude"
  )

  private def isImmutableBuilder(className: String): Boolean =
    ImmutableBuilders.contains(className.replace('$', '.'))

  def check(typing: Typing, bodyContext: TypingBodyContext, node: AST.Node, term: Term): Unit =
    term match {
      case call: Call if typing.reportingEnabled =>
        (call.target.`type`, call.method.returnType) match {
          case (receiver: ClassType, result: ClassType)
              if isImmutableBuilder(receiver.name) && receiver.name == result.name =>
            bodyContext.warningReporter.setSourceFile(bodyContext.sourceFile)
            bodyContext.warningReporter.discardedBuilderResult(
              node.location, receiver.name.substring(receiver.name.lastIndexOf('.') + 1), call.method.name)
          case _ =>
        }
      case _ =>
    }
}
