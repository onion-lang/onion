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


  /**
   * Find all closure expressions in a block (recursively)
   */
  private def findClosures(node: AST.Node): List[AST.ClosureExpression] = {
    val result = mutable.ListBuffer[AST.ClosureExpression]()

    // One function object for the whole walk: passing the local `visit` as an argument
    // eta-expanded it into a new lambda at every node.
    var visitF: AST.Node => Unit = null
    def visit(n: AST.Node): Unit = n match {
      case closure: AST.ClosureExpression =>
        result += closure
        // Don't descend into nested closures - they will be handled separately
      case _ =>
        ASTWalk.visitChildren(n)(visitF)
    }
    visitF = visit

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
    // Names declared by local variable declarations in this closure body.
    val locallyDeclared = mutable.Set[String]()
    // Names that appear in at least one nested closure's reference set.
    // A locally-declared variable that is also captured by a nested closure
    // must NOT be excluded: it still needs boxing so mutations in the nested
    // closure are visible in the declaring closure.
    val capturedByNested = mutable.Set[String]()

    var visitF: AST.Node => Unit = null
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
          val nestedRefs = findReferencedVariables(closure.body, nestedExcluded)
          result ++= nestedRefs
          capturedByNested ++= nestedRefs
          return // Special handling done, don't use visitChildren

        case localVar: AST.LocalVariableDeclaration =>
          // Record this name as locally declared so direct uses of it within
          // the same closure body are not mistaken for outer-scope captures.
          locallyDeclared += localVar.name
          if (localVar.init != null) visit(localVar.init)
          return

        case _ =>
      }
      ASTWalk.visitChildren(n)(visitF)
    }
    visitF = visit

    visit(node)
    // Exclude variables that are both locally declared AND not captured by any
    // nested closure. A locally-declared variable that IS captured by a nested
    // closure must stay in the result so it gets marked as boxed (the nested
    // closure needs to read/write through the same heap cell).
    (result.toSet -- (locallyDeclared.toSet -- capturedByNested.toSet))
  }
}
