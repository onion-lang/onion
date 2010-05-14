package onion.lang.syntax

abstract sealed class ASTNode(val pos: Position)
case class Position(line: Int, column: Int)
