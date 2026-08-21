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
  def scan(block: AST.BlockExpression, parameterNames: Set[String] = Set.empty): Set[String] =
    scanElements(block.elements, parameterNames)

  /**
   * Scans a sequence of block elements (e.g. the top-level statements of a
   * compilation unit, which are not wrapped in a single BlockExpression) and
   * returns the set of variable names captured by closures within them.
   */
  def scanElements(elements: Seq[AST.BlockElement], parameterNames: Set[String] = Set.empty): Set[String] = {
    val captured = mutable.Set[String]()

    // Find all closures across the elements
    val closures = elements.flatMap(findClosures)

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

  /** Traverse children of an AST node - common traversal logic extracted */
  private def visitChildren(n: AST.Node)(visit: AST.Node => Unit): Unit = n match {
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

  /**
   * Find all closure expressions in a block (recursively)
   */
  private def findClosures(node: AST.Node): List[AST.ClosureExpression] = {
    val result = mutable.ListBuffer[AST.ClosureExpression]()

    def visit(n: AST.Node): Unit = n match {
      case closure: AST.ClosureExpression =>
        result += closure
        // Don't descend into nested closures - they will be handled separately
      case _ =>
        visitChildren(n)(visit)
    }

    visit(node)
    result.toList
  }

  /**
   * Find all variable references in a closure body, including references made
   * only by a closure nested inside it (at any depth). A variable mutated
   * solely by a doubly-(or deeper-)nested closure still needs boxing in the
   * scope that declares it -- the immediately-enclosing closure merely relays
   * a reference to it without itself naming the variable, so stopping at one
   * level (as this used to) silently missed it. Each nested closure's own
   * parameters shadow any outer variable of the same name, so they are
   * excluded from the result rather than treated as captures.
   */
  private def findReferencedVariables(node: AST.Node, excluded: Set[String] = Set.empty): Set[String] = {
    val result = mutable.Set[String]()
    // Track names declared by local variable declarations inside this closure
    // body so they are not mistaken for outer-scope captures.
    val locallyDeclared = mutable.Set[String]()

    def visit(n: AST.Node): Unit = {
      n match {
        case id: AST.Id =>
          if (!excluded.contains(id.name)) result += id.name

        case assign: AST.Assignment =>
          // Check if lhs is an Id (simple variable assignment)
          assign.lhs match {
            case id: AST.Id => if (!excluded.contains(id.name)) result += id.name
            case other => visit(other)
          }
          visit(assign.rhs)
          return // Special handling done, don't use visitChildren

        case closure: AST.ClosureExpression =>
          val nestedExcluded = excluded ++ closure.args.map(_.name)
          result ++= findReferencedVariables(closure.body, nestedExcluded)
          return // Special handling done, don't use visitChildren

        case localVar: AST.LocalVariableDeclaration =>
          // Record that this name is declared in the current closure scope so
          // uses of it are not treated as captures of an outer variable.
          locallyDeclared += localVar.name
          if (localVar.init != null) visit(localVar.init)
          return

        case _ =>
      }
      visitChildren(n)(visit)
    }

    visit(node)
    (result -- locallyDeclared).toSet
  }
}
