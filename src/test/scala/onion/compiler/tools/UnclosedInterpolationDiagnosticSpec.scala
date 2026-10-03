package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Regression test for issue #1953: an unclosed `#{` interpolation in a string
 * literal must be reported at the string, not as an unrelated top-level
 * syntax error pointing at column 1 of the next identifier.
 */
class UnclosedInterpolationDiagnosticSpec extends AnyFunSpec {

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def compile(code: String) =
    new OnionCompiler(newConfig).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )

  describe("an unclosed #{ interpolation") {
    it("is reported at the string, not as a generic top-level syntax error") {
      val result = compile(
        """
          |IO::println("oops #{1 + 2")
          |""".stripMargin
      )
      assert(result.hasErrors)
      val errors = result.allErrors
      assert(errors.exists(_.message.contains("#{")), s"expected a message naming '#{', got: ${errors.map(_.message)}")
      // The failure is on line 2 at the '#' of '#{', not line 1 column 1 (the
      // generic top-level-declaration error this issue reports).
      assert(errors.exists(e => e.location != null && e.location.line == 2 && e.location.column > 1),
        s"expected the error anchored at the string's '#{', got: ${errors.map(e => (e.location, e.message))}")
    }

    it("is reported for a multi-line (triple-quoted) string too") {
      val result = compile(
        "\nIO::println(\"\"\"oops #{1 + 2\"\"\")\n"
      )
      assert(result.hasErrors)
      val errors = result.allErrors
      assert(errors.exists(_.message.contains("#{")), s"expected a message naming '#{', got: ${errors.map(_.message)}")
    }

    it("still parses a literal #{ that is not meant as interpolation when followed by a closing brace") {
      // Sanity check: a *closed* #{...} must keep working (no false positive).
      val result = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: Int = 3
          |    return "value is #{a}"
          |  }
          |}
          |""".stripMargin
      )
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
    }
  }
}
