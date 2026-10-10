package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.compiler.toolbox.Message
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Regression test (#2023): a string literal containing an escape sequence the
 * `<STRING>` grammar doesn't support (e.g. `\d`, as a user might type expecting
 * a regex) must be reported as an unsupported escape sequence, not as an
 * "unterminated string literal" -- the closing quote is right there on the
 * same line, so claiming the string never closed sends the reader looking for
 * a missing `"` that isn't missing.
 *
 * The expected text is resolved via `Message(...)`, the same lookup the
 * production code uses, so this holds under both the `en` and `ja` full-suite
 * runs instead of hardcoding English text that only matches one locale.
 */
class InvalidStringEscapeDiagnosticSpec extends AnyFunSpec {

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def compile(code: String) =
    new OnionCompiler(newConfig).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )

  describe("a string literal with an unsupported escape sequence") {
    it("is reported as an unsupported escape sequence, not an unterminated string literal") {
      val result = compile(
        """
          |val s = "\d+"
          |IO::println(s)
          |""".stripMargin
      )
      assert(result.hasErrors)
      val errors = result.allErrors
      val unterminated = Message("error.parsing.unterminated_string")
      val expected = Message("error.parsing.invalid_string_escape")
      assert(
        !errors.exists(_.message.contains(unterminated)),
        s"should not be reported as unterminated, got: ${errors.map(_.message)}"
      )
      assert(
        errors.exists(_.message.contains(expected)),
        s"expected a message containing '$expected', got: ${errors.map(_.message)}"
      )
    }

    it("is reported the same way when the bad escape is not the last thing in the string") {
      val result = compile(
        """
          |val s = "abc\def"
          |IO::println(s)
          |""".stripMargin
      )
      assert(result.hasErrors)
      val errors = result.allErrors
      val expected = Message("error.parsing.invalid_string_escape")
      assert(
        errors.exists(_.message.contains(expected)),
        s"expected a message containing '$expected', got: ${errors.map(_.message)}"
      )
    }

    it("still parses a string with only supported escapes (no false positive)") {
      val result = compile(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s: String = "a\nb\tc\\d\"e"
          |    return s
          |  }
          |}
          |""".stripMargin
      )
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
    }
  }
}
