package onion.compiler.typing

import onion.compiler.{AST, ASTWalk}

/**
 * Detects return statements in AST nodes.
 *
 * This is used to determine if a closure body contains explicit return
 * statements, which affects return type inference.
 *
 * Note: Nested closures are NOT inspected - returns in inner closures
 * don't affect the outer closure's return type.
 */
private[typing] object ReturnNodeDetector {

  /**
   * Check if an AST node contains any return statements.
   * Does not descend into nested closures.
   */
  def containsReturn(node: AST.Node): Boolean = {
    var found = false


    // One function object for the whole walk (passing the local def eta-expanded a lambda per node).
    var visitF: AST.Node => Unit = null
    def visit(n: AST.Node): Unit = n match {
      case _: AST.ReturnExpression =>
        found = true
      case _: AST.ClosureExpression =>
        () // do not inspect nested closures
      case _ =>
        ASTWalk.visitChildren(n)(visitF)
    }
    visitF = visit

    visit(node)
    found
  }
}
