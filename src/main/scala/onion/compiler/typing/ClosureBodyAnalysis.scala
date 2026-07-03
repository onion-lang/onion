package onion.compiler.typing

import onion.compiler.AST

/**
 * AST-level analysis of closure bodies used to decide whether a closure can be
 * typed eagerly (with no expected type) or must be deferred until the target
 * parameter type is known.
 *
 * A closure whose body never returns normally (e.g. `() -> { throw ... }`)
 * infers a bottom return type. When typed eagerly, that bottom is widened to
 * `Object`, which over-constrains a generic method's type variable before the
 * expected return type can pin it (issue #233:
 * `val f: Future[String] = Future::async(() -> { throw ... })` used to infer
 * `Future[Object]`). Such closures are routed through bidirectional inference
 * instead, so the expected parameter/return type flows in first.
 */
private[typing] object ClosureBodyAnalysis {

  /**
   * Whether the closure body definitely completes abruptly on every path,
   * i.e. it never falls through to produce a normal value. Only clearly-abrupt
   * constructs are recognised; anything unrecognised is treated as returning
   * normally (conservative: false).
   */
  def neverReturnsNormally(closure: AST.ClosureExpression): Boolean =
    closure.body != null && blockNeverCompletesNormally(closure.body)

  private def blockNeverCompletesNormally(block: AST.BlockExpression): Boolean =
    block.elements.lastOption.exists {
      case e: AST.Expression => exprNeverCompletesNormally(e)
      case _ => false
    }

  private def exprNeverCompletesNormally(node: AST.Node): Boolean = node match {
    case _: AST.ThrowExpression => true
    case _: AST.ReturnExpression => true
    case _: AST.BreakExpression => true
    case _: AST.ContinueExpression => true
    case block: AST.BlockExpression => blockNeverCompletesNormally(block)
    case ifExpr: AST.IfExpression =>
      ifExpr.elseBlock != null &&
        blockNeverCompletesNormally(ifExpr.thenBlock) &&
        blockNeverCompletesNormally(ifExpr.elseBlock)
    case _ => false
  }
}
