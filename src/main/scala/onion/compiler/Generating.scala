package onion.compiler

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/08
 * Time: 13:36:58
 * To change this template use File | Settings | File Templates.
 */

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

