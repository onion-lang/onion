package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Regression test for issue #2429: an empty `#{}` interpolation must be reported once, at
 * the `#{`, as an empty interpolation. It was "unexpected end of file" at line 1 (or at no
 * line at all), sending the reader after a missing brace that does not exist.
 */
class EmptyInterpolationDiagnosticSpec extends AnyFunSpec {

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def errorsOf(code: String) = {
    val result = new OnionCompiler(newConfig).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )
    assert(result.hasErrors, "expected a syntax error")
    result.allErrors
  }

  // Locale-agnostic: both message files name the `#{expr}` form the reader should write.
  private def assertEmptyAt(code: String, line: Int, column: Int, escapable: Boolean = true): Unit = {
    val errors = errorsOf(code)
    assert(errors.size == 1, s"expected exactly one error, got: ${errors.map(e => (e.location, e.message))}")
    val e = errors.head
    assert(e.location != null && e.location.line == line && e.location.column == column,
      s"expected the error at $line:$column (the '#'), got: ${(e.location, e.message)}")
    assert(e.message.contains("#{expr}"), s"expected the empty-interpolation message, got: ${e.message}")
    // Only an ordinary string can escape the '#'; a triple-quoted one has no escapes.
    assert(e.message.contains("\\#{}") == escapable, s"escape advice should be ${if (escapable) "present" else "absent"}: ${e.message}")
  }

  describe("an empty #{} interpolation") {
    it("is reported at the '#' on its own line, not at line 1") {
      assertEmptyAt("val n = 2\nprintln(\"a #{} b\")\n", 2, 12)
    }

    it("is reported with a location when it is on the first line") {
      assertEmptyAt("println(\"a #{} b\")\n", 1, 12)
    }

    it("is reported when the braces hold only blanks") {
      assertEmptyAt("val n = 2\nval m = 3\nprintln(\"a #{   } b\")\n", 3, 12)
    }

    it("is reported after an earlier, valid interpolation in the same string") {
      assertEmptyAt("val n = 2\nprintln(\"#{n} and #{}\")\n", 2, 19)
    }

    it("is reported in a triple-quoted string, on the line it is on") {
      assertEmptyAt("val n = 2\nprintln(\"\"\"first\n  then #{} here\"\"\")\n", 3, 8, escapable = false)
    }

    it("leaves a non-empty interpolation alone") {
      val result = new OnionCompiler(newConfig).compileDetailed(
        Seq(new StreamInputSource(() => new StringReader("val n = 2\nprintln(\"a #{ n } b\")\n"), "Test.on"))
      )
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
    }
  }
}
