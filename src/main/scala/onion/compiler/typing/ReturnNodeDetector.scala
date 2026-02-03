package onion.compiler.typing

import onion.compiler.AST

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

    def visitChildren(n: AST.Node)(visit: AST.Node => Unit): Unit = n match {
      case block: AST.BlockExpression =>
        block.elements.foreach(visit)

      case ifExpr: AST.IfExpression =>
        visit(ifExpr.condition)
        visit(ifExpr.thenBlock)
        if (ifExpr.elseBlock != null) visit(ifExpr.elseBlock)

      case whileExpr: AST.WhileExpression =>
        visit(whileExpr.condition)
        visit(whileExpr.block)

      case foreachExpr: AST.ForeachExpression =>
        visit(foreachExpr.collection)
        visit(foreachExpr.statement)

      case forExpr: AST.ForExpression =>
        if (forExpr.init != null) visit(forExpr.init)
        if (forExpr.condition != null) visit(forExpr.condition)
        if (forExpr.update != null) visit(forExpr.update)
        visit(forExpr.block)

      case assign: AST.Assignment =>
        visit(assign.lhs)
        visit(assign.rhs)

      case localVar: AST.LocalVariableDeclaration =>
        if (localVar.init != null) visit(localVar.init)

      case binary: AST.BinaryExpression =>
        visit(binary.lhs)
        visit(binary.rhs)

      case unary: AST.UnaryExpression =>
        visit(unary.term)

      case call: AST.MethodCall =>
        if (call.target != null) visit(call.target)
        call.args.foreach(visit)

      case call: AST.UnqualifiedMethodCall =>
        call.args.foreach(visit)

      case call: AST.StaticMethodCall =>
        call.args.foreach(visit)

      case call: AST.SuperMethodCall =>
        call.args.foreach(visit)

      case newObj: AST.NewObject =>
        newObj.args.foreach(visit)

      case newArray: AST.NewArray =>
        newArray.args.foreach(visit)

      case newArrayWithValues: AST.NewArrayWithValues =>
        newArrayWithValues.values.foreach(visit)

      case listLit: AST.ListLiteral =>
        listLit.elements.foreach(visit)

      case cast: AST.Cast =>
        visit(cast.src)

      case isInstance: AST.IsInstance =>
        visit(isInstance.target)

      case memberSel: AST.MemberSelection =>
        if (memberSel.target != null) visit(memberSel.target)

      case returnExpr: AST.ReturnExpression =>
        if (returnExpr.result != null) visit(returnExpr.result)

      case throwExpr: AST.ThrowExpression =>
        visit(throwExpr.target)

      case tryExpr: AST.TryExpression =>
        visit(tryExpr.tryBlock)
        tryExpr.recClauses.foreach { case (_, block) =>
          visit(block)
        }
        if (tryExpr.finBlock != null) visit(tryExpr.finBlock)

      case syncExpr: AST.SynchronizedExpression =>
        visit(syncExpr.condition)
        visit(syncExpr.block)

      case selectExpr: AST.SelectExpression =>
        visit(selectExpr.condition)
        selectExpr.cases.foreach { case (exprs, block) =>
          exprs.foreach(visit)
          visit(block)
        }
        if (selectExpr.elseBlock != null) visit(selectExpr.elseBlock)

      case exprBox: AST.ExpressionBox =>
        visit(exprBox.body)

      case stringInterp: AST.StringInterpolation =>
        stringInterp.expressions.foreach(visit)

      case _ =>
        ()
    }

    def visit(n: AST.Node): Unit = n match {
      case _: AST.ReturnExpression =>
        found = true
      case _: AST.ClosureExpression =>
        () // do not inspect nested closures
      case _ =>
        visitChildren(n)(visit)
    }

    visit(node)
    found
  }
}
