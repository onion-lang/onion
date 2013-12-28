package onion.compiler

class Generating(config: CompilerConfig) extends AnyRef with ProcessingUnit[Array[IRT.ClassDefinition], Array[CompiledClass]] {
  class CodeGeneratingEnvironment
  type Environment = CodeGeneratingEnvironment
  def newEnvironment(source: Array[IRT.ClassDefinition]): CodeGeneratingEnvironment = {
    new CodeGeneratingEnvironment
  }

  private val generator = new CodeGeneration(config)
  def doProcess(source: Array[IRT.ClassDefinition], environment: CodeGeneratingEnvironment): Array[CompiledClass] = {
    generator.process(source)
  }
}

