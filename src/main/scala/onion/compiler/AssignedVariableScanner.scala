package onion.compiler

import scala.collection.mutable

/**
 * Scans untyped AST for simple names that are targets of assignments,
 * compound assignments or ++/--.
 *
 * Used to decide whether method parameters can participate in smart casts:
 * a parameter that is never assigned behaves like a val, so `if p != null`
 * and `if p is T` can safely narrow it.
 */
object AssignedVariableScanner {

  private val comparisonSymbols = Set("==", "!=", "<=", ">=", "===", "!==")

  def scan(node: AST.Node): Set[String] =
    scan(node, new java.util.IdentityHashMap[AST.Node, Set[String]]())

  /**
   * The same answer, computed bottom-up with every visited node recorded in `memo`.
   *
   * Body typing asks this question for a method body and then again for each `if`,
   * `while` and `select` branch inside it, so a plain top-down scan re-walked the same
   * subtrees at every nesting level. Recording the answer for every node makes the
   * nested questions lookups, and the memo lives on the typing context, so the whole
   * unit is walked once. Subtrees without an assignment share the empty set.
   */
  def scan(node: AST.Node, memo: java.util.IdentityHashMap[AST.Node, Set[String]]): Set[String] = {
    def union(a: Set[String], b: Set[String]): Set[String] =
      if (b.isEmpty) a else if (a.isEmpty) b else a ++ b

    def target(e: AST.Expression, acc: Set[String]): Set[String] = e match {
      case id: AST.Id => acc + id.name
      case _ => acc // field/index assignment doesn't rebind the local name
    }

    def visitNode(n: AST.Node): Set[String] = {
      val cached = memo.get(n)
      if (cached != null) return cached
      var acc: Set[String] = n match {
        case b: AST.BinaryExpression if b.symbol.endsWith("=") && !comparisonSymbols.contains(b.symbol) =>
          target(b.lhs, Set.empty)
        case u: AST.UnaryExpression if u.symbol == "++" || u.symbol == "--" =>
          target(u.term, Set.empty)
        case _ => Set.empty
      }
      n match {
        case p: Product =>
          val it = p.productIterator
          while (it.hasNext) acc = union(acc, visitAny(it.next()))
        case _ =>
      }
      memo.put(n, acc)
      acc
    }

    def visitAny(any: Any): Set[String] = any match {
      case n: AST.Node => visitNode(n)
      case s: Iterable[_] =>
        var acc: Set[String] = Set.empty
        s.foreach(e => acc = union(acc, visitAny(e)))
        acc
      case a: Array[_] =>
        var acc: Set[String] = Set.empty
        a.foreach(e => acc = union(acc, visitAny(e)))
        acc
      case p: Product => // tuples in case classes
        var acc: Set[String] = Set.empty
        val it = p.productIterator
        while (it.hasNext) acc = union(acc, visitAny(it.next()))
        acc
      case _ => Set.empty
    }

    visitNode(node)
  }
}
