package onion.lang.syntax

abstract sealed class ASTElement(val pos: Position)
case class ASTCompilationUnit(pos: Position, sourceFile: String = "<generated>", imports: ASTImportClause, toplevels: List[ASTToplevel])
case class ASTImportClause(pos: Position, mapping: List[(String, String)])
abstract sealed class ASTToplevel(pos: Position)
case class Position(line: Int, column: Int)
