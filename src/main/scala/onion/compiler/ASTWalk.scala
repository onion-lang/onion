package onion.compiler

/**
 * Structural traversal of the untyped AST: `visitChildren(n)(visit)` calls `visit` on each
 * direct child of `n`, and nothing else. A scan decides at each node whether to recurse
 * (usually by calling `visitChildren(n)(visit)` again from its own `visit`), so that stopping
 * at closures, declarations or any other node kind is the scan's decision, not the walk's.
 *
 * Shared by [[CapturedVariableScanner]] and `typing.ReturnNodeDetector`, which each used to
 * carry an identical copy of this match.
 */
object ASTWalk {
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

    case mapLit: AST.MapLiteral =>
      mapLit.entries.foreach { case (k, v) => visit(k); visit(v) }

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

    case stringInterp: AST.StringInterpolation =>
      stringInterp.expressions.foreach(visit)

    case _ =>
      // Literals, identifiers, closures, etc. - no children to visit
  }
}
