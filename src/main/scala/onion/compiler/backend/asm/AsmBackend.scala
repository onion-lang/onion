package onion.compiler.backend.asm

import onion.compiler.{BytecodeGenerator, CompiledClass, CompilerConfig, TypedAST}
import org.objectweb.asm.{Type => AsmType}

/**
 * Primary ASM backend boundary for bytecode generation.
 *
 * Pipeline-facing code should depend on this boundary. The root-level
 * `onion.compiler.AsmCodeGeneration` now exists only as a public compatibility
 * facade over the implementation in this package.
 */
class AsmBackend(config: CompilerConfig) extends BytecodeGenerator {
  private val generator = new AsmCodeGeneration(config)

  override def process(classes: Seq[TypedAST.ClassDefinition]): Seq[CompiledClass] =
    generator.process(classes)
}

object AsmBackend {
  def asmType(tp: TypedAST.Type): AsmType =
    AsmCodeGeneration.asmType(tp)

  def boxClassName(tp: TypedAST.Type): String =
    AsmCodeGeneration.boxClassName(tp)

  def boxAsmType(tp: TypedAST.Type): AsmType =
    AsmCodeGeneration.boxAsmType(tp)
}
