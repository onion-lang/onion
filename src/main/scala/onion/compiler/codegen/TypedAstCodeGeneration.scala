package onion.compiler.codegen

import onion.compiler.{AsmCodeGeneration, BytecodeGenerator, CompiledClass, CompilerConfig, TypedAST}

/**
 * Primary typed-AST to bytecode boundary used by the compiler pipeline.
 *
 * The legacy TypedGenerating adapter still exists for compatibility, but
 * pipeline code should depend on this direct generator instead.
 */
class TypedAstCodeGeneration(config: CompilerConfig) extends BytecodeGenerator {
  private val backend = new AsmCodeGeneration(config)

  override def process(classes: Seq[TypedAST.ClassDefinition]): Seq[CompiledClass] =
    backend.process(classes)
}
