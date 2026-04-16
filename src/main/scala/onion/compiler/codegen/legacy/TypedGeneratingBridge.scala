package onion.compiler.codegen.legacy

import onion.compiler.codegen.TypedAstCodeGeneration
import onion.compiler.{CompiledClass, CompilerConfig, Processor, TypedAST}

/**
 * Legacy compatibility bridge for historical `TypedGenerating` entry points.
 *
 * New compiler code should use `TypedAstCodeGeneration` or the pipeline-level
 * `BytecodeGenerationPhase` directly.
 */
class TypedGeneratingBridge(config: CompilerConfig)
  extends AnyRef
    with Processor[Seq[TypedAST.ClassDefinition], Seq[CompiledClass]] {
  private val generator = new TypedAstCodeGeneration(config)

  type Environment = Unit

  override def newEnvironment(source: Seq[TypedAST.ClassDefinition]): Environment =
    ()

  override def processBody(source: Seq[TypedAST.ClassDefinition], environment: Environment): Seq[CompiledClass] =
    generator.process(source)
}
