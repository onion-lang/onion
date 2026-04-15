package onion.compiler.pipeline

import onion.compiler.{AST, TypedAST}

final case class DebugArtifacts(
  parsedUnits: Option[Seq[AST.CompilationUnit]] = None,
  rewrittenUnits: Option[Seq[AST.CompilationUnit]] = None,
  typedClasses: Option[Seq[TypedAST.ClassDefinition]] = None,
  typedBindings: Option[Map[AST.Node, TypedAST.Node]] = None
)
