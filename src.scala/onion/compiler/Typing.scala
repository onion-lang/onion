package onion.compiler

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/05
 * Time: 10:46:42
 * To change this template use File | Settings | File Templates.
 */
class Typing(config: CompilerConfig) extends AnyRef
  with ProcessingUnit[Array[AST.CompilationUnit], Array[IxCode.ClassDefinition]] {
  type Environment = TypingEnvironment
  class TypingEnvironment(val reporter: SemanticErrorReporter)
  def newEnvironment(source: Array[AST.CompilationUnit]) = new TypingEnvironment(
    new SemanticErrorReporter(config.getMaxErrorReports)
  )
  def doProcess(source: Array[AST.CompilationUnit], environment: TypingEnvironment): Array[IxCode.ClassDefinition] = {
    null
  }
}
