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

  def scan(node: AST.Node): Set[String] = {
    val assigned = mutable.Set[String]()

    def markTarget(e: AST.Expression): Unit = e match {
      case id: AST.Id => assigned += id.name
      case _ => // field/index assignment doesn't rebind the local name
    }

    def visit(any: Any): Unit = any match {
      case n: AST.Node =>
        n match {
          case b: AST.BinaryExpression if b.symbol.endsWith("=") && !comparisonSymbols.contains(b.symbol) =>
            markTarget(b.lhs)
          case u: AST.UnaryExpression if u.symbol == "++" || u.symbol == "--" =>
            markTarget(u.term)
          case _ =>
        }
        n match {
          case p: Product => p.productIterator.foreach(visit)
          case _ =>
        }
      case s: Iterable[_] => s.foreach(visit)
      case a: Array[_] => a.foreach(visit)
      case p: Product => p.productIterator.foreach(visit) // tuples in case classes
      case _ =>
    }

    visit(node)
    assigned.toSet
  }
}
