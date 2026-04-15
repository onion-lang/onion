package onion.compiler.codegen

import onion.compiler.backend.asm.AsmBackend
import onion.compiler.{BytecodeGenerator, CompiledClass, CompilerConfig, TypedAST}

/**
 * Primary typed-AST to bytecode boundary used by the compiler pipeline.
 *
 * The legacy TypedGenerating adapter still exists for compatibility, but new
 * pipeline code should depend on this direct generator and the backend/asm
 * boundary instead of wiring to `AsmCodeGeneration` directly.
 */
class TypedAstCodeGeneration(config: CompilerConfig) extends BytecodeGenerator {
  private val backend = new AsmBackend(config)

  override def process(classes: Seq[TypedAST.ClassDefinition]): Seq[CompiledClass] =
    backend.process(classes)
}
