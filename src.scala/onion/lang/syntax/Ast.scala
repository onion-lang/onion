package onion.lang.syntax

object Ast {
  abstract sealed class Node(val pos: Position)
  case class Position(line: Int, column: Int)
}
