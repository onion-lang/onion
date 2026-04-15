package onion.compiler.backend

import onion.compiler.codegen.TypedAstCodeGeneration
import onion.compiler.pipeline.{CompilerPhase, PhaseContext}
import onion.compiler.{CompiledClass, CompilerConfig, TypedAST}

final class BytecodeGenerationPhase(config: CompilerConfig)
  extends CompilerPhase[Seq[TypedAST.ClassDefinition], Seq[CompiledClass]] {
  override val name: String = "BytecodeGeneration"

  override def run(input: Seq[TypedAST.ClassDefinition], ctx: PhaseContext): Seq[CompiledClass] =
    new TypedAstCodeGeneration(config).process(input)
}
