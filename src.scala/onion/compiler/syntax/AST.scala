package onion.lang.syntax

abstract sealed class ASTNode(val pos: Position)
case class ASTPosition(line: Int, column: Int)
