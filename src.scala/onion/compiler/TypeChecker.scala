package onion.compiler

import error.SemanticErrorReporter

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/05
 * Time: 10:46:42
 * To change this template use File | Settings | File Templates.
 */

class TypeChecker(config: CompilerConfig) {
  private[this] val reporter = new SemanticErrorReporter(config.getMaxErrorReports)

  def process(units: Array[AST.CompilationUnit]): Array[IxCode.ClassDefinition] = {
    null
  }
}
