package onion.compiler

/** Entry point for bytecode generation from typed AST using ASM. */
class TypedGenerating(config: CompilerConfig)
  extends AnyRef
    with Processor[Seq[TypedAST.ClassDefinition], Seq[CompiledClass]]:
  class TypedGeneratingEnvironment
  type Environment = TypedGeneratingEnvironment
  def newEnvironment(source: Seq[TypedAST.ClassDefinition]): TypedGeneratingEnvironment =
    new TypedGeneratingEnvironment

  private val generator: BytecodeGenerator = new AsmCodeGeneration(config)

  def processBody(source: Seq[TypedAST.ClassDefinition], environment: TypedGeneratingEnvironment): Seq[CompiledClass] =
    generator.process(source)

