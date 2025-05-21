package onion.compiler

/**
 * Bridge generator that accepts typed AST and delegates to the old
 * CodeGeneration using IRT nodes. This eases migration away from IRT
 * without rewriting the backend in one go.
 */
class TypedGenerating(config: CompilerConfig)
  extends AnyRef
    with Processor[Seq[TypedAST.ClassDefinition], Seq[CompiledClass]]:
  class TypedGeneratingEnvironment
  type Environment = TypedGeneratingEnvironment
  def newEnvironment(source: Seq[TypedAST.ClassDefinition]): TypedGeneratingEnvironment =
    new TypedGeneratingEnvironment

  private val generator: BytecodeGenerator =
    if sys.props.get("onion.asm").exists(_.toBoolean) then
      new AsmCodeGeneration(config)
    else
      new CodeGeneration(config)

  def processBody(source: Seq[TypedAST.ClassDefinition], environment: TypedGeneratingEnvironment): Seq[CompiledClass] =
    generator.process(source)
