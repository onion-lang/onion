package onion.compiler

class Generating(config: CompilerConfig) extends AnyRef with ProcessingUnit[Seq[IRT.ClassDefinition], Seq[CompiledClass]] {
  class CodeGeneratingEnvironment
  type Environment = CodeGeneratingEnvironment
  def newEnvironment(source: Seq[IRT.ClassDefinition]): CodeGeneratingEnvironment = {
    new CodeGeneratingEnvironment
  }

  private val generator = new CodeGeneration(config)
  def doProcess(source: Seq[IRT.ClassDefinition], environment: CodeGeneratingEnvironment): Seq[CompiledClass] = {
    generator.process(source)
  }
}

