package onion.compiler

import onion.compiler.diagnostics.DiagnosticRenderer

import java.io.PrintStream

object DiagnosticsPrinter {
  def dumpAst(units: Seq[AST.CompilationUnit], out: PrintStream = System.err): Unit =
    DiagnosticRenderer.dumpAst(units, out)

  def dumpTyped(classes: Seq[TypedAST.ClassDefinition], out: PrintStream = System.err): Unit =
    DiagnosticRenderer.dumpTyped(classes, out)
}
