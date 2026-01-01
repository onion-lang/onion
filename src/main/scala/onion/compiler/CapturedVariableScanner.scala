package onion.compiler

import scala.collection.mutable

/**
 * Scans untyped AST to find variables that are captured by closures.
 * This is used during typing to determine which mutable variables need to be boxed.
 */
object CapturedVariableScanner {

  /**
   * Scans a method/function block and returns the set of variable names
   * that are captured by closures within that block.
   *
   * @param block The method/function body
   * @param parameterNames Names of method parameters (should not be boxed)
   * @return Set of variable names that are captured by closures
   */
  def scan(block: AST.BlockExpression, parameterNames: Set[String] = Set.empty): Set[String] = {
    val captured = mutable.Set[String]()

    // Find all closures in the block
    val closures = findClosures(block)

    // For each closure, collect variables it references
    for (closure <- closures) {
      val closureParams = closure.args.map(_.name).toSet
      val referenced = findReferencedVariables(closure.body)

      // A variable is captured if:
      // 1. It's referenced in the closure
      // 2. It's not a closure parameter
      // 3. It's not a method parameter (those are already in slots)
      for (varName <- referenced) {
        if (!closureParams.contains(varName) && !parameterNames.contains(varName)) {
          captured += varName
        }
      }
    }

    captured.toSet
  }

  /**
   * Find all closure expressions in a block (recursively)
   */
  private def findClosures(node: AST.Node): List[AST.ClosureExpression] = {
    val result = mutable.ListBuffer[AST.ClosureExpression]()

    def visit(n: AST.Node): Unit = n match {
      case closure: AST.ClosureExpression =>
        result += closure
        // Don't descend into nested closures - they will be handled separately

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

      case indexing: AST.Indexing =>
        visit(indexing.lhs)
        visit(indexing.rhs)

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
        // Literals, identifiers, etc. - no children to visit
    }

    visit(node)
    result.toList
  }

  /**
   * Find all variable references in a closure body
   */
  private def findReferencedVariables(node: AST.Node): Set[String] = {
    val result = mutable.Set[String]()

    def visit(n: AST.Node): Unit = n match {
      case id: AST.Id =>
        result += id.name

      case assign: AST.Assignment =>
        // Check if lhs is an Id (simple variable assignment)
        assign.lhs match {
          case id: AST.Id => result += id.name
          case other => visit(other)
        }
        visit(assign.rhs)

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

      case indexing: AST.Indexing =>
        visit(indexing.lhs)
        visit(indexing.rhs)

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

      case _: AST.ClosureExpression =>
        // Don't descend into nested closures

      case _ =>
        // Literals, etc. - no variables to reference
    }

    visit(node)
    result.toSet
  }
}
