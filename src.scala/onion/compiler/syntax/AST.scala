package onion.lang.syntax

object AST {
  abstract sealed class Node{ def pos: Position }
  case class CompilationUnit(
    pos: Position, sourceFile: String = "<generated>", module: ModuleDeclaration,
    imports: ImportClause, toplevels: List[Toplevel]) extends Node
  case class ModuleDeclaration(pos: Position, name: String) extends Node
  case class ImportClause(pos: Position, mapping: List[(String, String)]) extends Node
  abstract sealed class Toplevel(pos: Position) extends Node
  case class Position(line: Int, column: Int)
}  