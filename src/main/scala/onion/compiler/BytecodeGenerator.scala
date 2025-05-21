package onion.compiler

trait BytecodeGenerator:
  def process(classes: Seq[TypedAST.ClassDefinition]): Seq[CompiledClass]
