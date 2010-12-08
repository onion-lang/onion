package onion.compiler

import pass.CodeGeneration

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/08
 * Time: 13:36:58
 * To change this template use File | Settings | File Templates.
 */

class Generating(config: CompilerConfig) extends AnyRef with ProcessingUnit[Array[IxCode.ClassDefinition], Array[CompiledClass]] {
  class CodeGeneratingEnvironment
  type Environment = CodeGeneratingEnvironment
  def newEnvironment(source: Array[IxCode.ClassDefinition]): CodeGeneratingEnvironment = {
    new CodeGeneratingEnvironment
  }
  private val generator = new CodeGeneration(config)
  def doProcess(source: Array[IxCode.ClassDefinition], environment: CodeGeneratingEnvironment): Array[CompiledClass] = {
    generator.process(source)
  }
}

