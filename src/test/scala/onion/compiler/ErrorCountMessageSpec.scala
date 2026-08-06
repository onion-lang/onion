package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

/**
 * `error.count` is printed as the trailer after a failed compilation
 * (`DiagnosticRenderer.printErrors`, `Repl`). The English bundle used a bare
 * `"{0} errors are found."` template regardless of count, which reads as
 * "1 errors are found." for a single-error compile.
 */
class ErrorCountMessageSpec extends AnyFunSpec with Diagrams {
  it("uses singular wording for a single error in English") {
    val text = onion.compiler.toolbox.Message("error.count", 1)
    assert(!text.contains("1 errors"))
  }

  it("uses plural wording for multiple errors in English") {
    val text = onion.compiler.toolbox.Message("error.count", 3)
    assert(text.contains("3 errors"))
  }
}
