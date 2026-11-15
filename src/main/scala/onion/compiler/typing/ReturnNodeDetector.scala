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
      // Keep the reach this detector had before #3024 widened ASTWalk. A lambda with an early
      // return drops its last expression (#3025), so seeing a return in these nodes would turn
      // a correct result into `null`. Remove these cases once #3025 is fixed.
      case _: AST.DoWhileExpression | _: AST.LabeledLoop | _: AST.SafeMethodCall |
           _: AST.SafeMemberSelection | _: AST.TraitMethodCall | _: AST.ForInitDeclaration |
           _: AST.ForInitExpression | _: AST.ExpressionPattern | _: AST.GuardedPattern |
           _: AST.DestructuringPattern | _: AST.YieldStatement | _: AST.DoExpression |
           _: AST.DoBinding | _: AST.DestructuringDeclaration =>
        ()
      case t: AST.TryExpression =>
        visit(t.tryBlock) // not the resources, as before #3024
        t.recClauses.foreach { case (_, block) => visit(block) }
        if (t.finBlock != null) visit(t.finBlock)
      case _ =>
        ASTWalk.visitChildren(n)(visitF)
    }
    visitF = visit

    visit(node)
    found
  }
}
