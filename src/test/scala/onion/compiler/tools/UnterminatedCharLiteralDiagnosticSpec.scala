package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.compiler.toolbox.Message
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Regression test: an unclosed character literal (e.g. `'x` with no closing
 * quote) must be reported as such, not as an unrelated "expecting <ID>, ..."
 * syntax error. A complete `'x'` always lexes as a single CHARACTER token, so
 * a lone `'` reaching the parser can only mean the literal never closed --
 * exactly the reasoning `error.parsing.unterminated_string` already applies
 * to a lone `"`.
 *
 * The expected text is resolved via `Message(...)`, the same lookup the
 * production code uses, so this holds under both the `en` and `ja` full-suite
 * runs instead of hardcoding English text that only matches one locale.
 */
class UnterminatedCharLiteralDiagnosticSpec extends AnyFunSpec {

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def compile(code: String) =
    new OnionCompiler(newConfig).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )

  describe("an unclosed character literal") {
    it("is reported as an unterminated character literal, not a generic syntax error") {
      val result = compile(
        """
          |val c: Int = 'x
          |""".stripMargin
      )
      assert(result.hasErrors)
      val errors = result.allErrors
      val expected = Message("error.parsing.unterminated_char")
      assert(
        errors.exists(_.message.contains(expected)),
        s"expected a message containing '$expected', got: ${errors.map(_.message)}"
      )
    }

    it("still parses a complete character literal (no false positive)") {
      val result = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val c: Int = 'x'
          |    return "" + c
          |  }
          |}
          |""".stripMargin
      )
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
    }
  }
}
