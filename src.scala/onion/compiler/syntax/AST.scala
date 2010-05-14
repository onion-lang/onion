package onion.lang.syntax

abstract sealed class ASTElement(val pos: Position)
case class Position(line: Int, column: Int)
