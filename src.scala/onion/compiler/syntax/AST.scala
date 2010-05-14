package onion.lang.syntax

abstract sealed class ASTElement(val pos: Position)
case class ASTCompilationUnit(
  pos: Position, sourceFile: String = "<generated>", module: ModuleDeclaration,
  imports: ASTImportClause, toplevels: List[ASTToplevel])
case class ASTModuleDeclaration(pos: Position, name: String)
case class ASTImportClause(pos: Position, mapping: List[(String, String)])
abstract sealed class ASTToplevel(pos: Position)
case class Position(line: Int, column: Int)
