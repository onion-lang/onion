package onion.compiler

import onion.compiler.codegen.TypedAstCodeGeneration

/**
 * Legacy compatibility adapter for callers that still instantiate
 * `onion.compiler.TypedGenerating` directly.
 *
 * The main compiler pipeline now targets `onion.compiler.codegen.TypedAstCodeGeneration`
 * instead of routing through this shim.
 */
class TypedGenerating(config: CompilerConfig)
  extends AnyRef
    with Processor[Seq[TypedAST.ClassDefinition], Seq[CompiledClass]]:
  private val generator = new TypedAstCodeGeneration(config)

  type Environment = Unit

  override def newEnvironment(source: Seq[TypedAST.ClassDefinition]): Environment =
    ()

  override def processBody(source: Seq[TypedAST.ClassDefinition], environment: Environment): Seq[CompiledClass] =
    generator.process(source)
