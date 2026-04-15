/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import onion.compiler.diagnostics.DiagnosticRenderer
import java.io.PrintStream

/**
 * Utility for printing compiler diagnostics in a consistent way.
 */
object CompilationReporter {

  def printErrors(errors: Seq[CompileError], out: PrintStream = Console.err): Unit =
    DiagnosticRenderer.printErrors(errors, out)

  def formatErrors(errors: Seq[CompileError]): Seq[String] =
    DiagnosticRenderer.formatErrors(errors)
}
