package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.compiler.toolbox.Message
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Regression test (#2257): an integer literal that overflows the range of the type its
 * suffix (or lack of one) selects must be reported with its own message -- what range it
 * needed and which suffix would fix it -- not a content-free "Syntax error. Encountered
 * ..., but expecting valid token", which happened because the literal-range check threw a
 * bare parser give-up signal with no expected-token information attached.
 *
 * The expected text is resolved via `Message(...)`, the same lookup the production code
 * uses, so this holds under both the `en` and `ja` full-suite runs instead of hardcoding
 * English text that only matches one locale.
 */
class IntegerLiteralOutOfRangeDiagnosticSpec extends AnyFunSpec {

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def compile(code: String) =
    new OnionCompiler(newConfig).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )

  private val genericSyntaxError = Message("error.parsing.syntax_error", "\n", "valid token")

  describe("an Int literal without a suffix that overflows Int range") {
    it("is reported as out of range for Int, not a generic syntax error") {
      val result = compile(
        """
          |def main: void {
          |  var x: Long = 99999999999
          |  IO::println(x)
          |}
          |""".stripMargin
      )
      assert(result.hasErrors)
      val errors = result.allErrors
      val expected = Message("error.parsing.int_literal_out_of_range")
      assert(
        !errors.exists(_.message.contains("valid token")),
        s"should not fall back to a content-free message, got: ${errors.map(_.message)}"
      )
      assert(
        errors.exists(_.message.contains(expected)),
        s"expected a message containing '$expected', got: ${errors.map(_.message)}"
      )
    }
  }

  describe("a Long literal (L suffix) that overflows Long range") {
    it("is reported as out of range for Long") {
      val result = compile(
        """
          |def main: void {
          |  var x: Long = 99999999999999999999999999L
          |  IO::println(x)
          |}
          |""".stripMargin
      )
      assert(result.hasErrors)
      val errors = result.allErrors
      val expected = Message("error.parsing.long_literal_out_of_range")
      assert(
        errors.exists(_.message.contains(expected)),
        s"expected a message containing '$expected', got: ${errors.map(_.message)}"
      )
    }
  }

  describe("a Byte literal (B suffix) that overflows Byte range") {
    it("is reported as out of range for Byte") {
      val result = compile(
        """
          |def main: void {
          |  var x: Byte = 999B
          |  IO::println(x)
          |}
          |""".stripMargin
      )
      assert(result.hasErrors)
      val errors = result.allErrors
      val expected = Message("error.parsing.byte_literal_out_of_range")
      assert(
        errors.exists(_.message.contains(expected)),
        s"expected a message containing '$expected', got: ${errors.map(_.message)}"
      )
    }
  }

  describe("a Short literal (S suffix) that overflows Short range") {
    it("is reported as out of range for Short") {
      val result = compile(
        """
          |def main: void {
          |  var x: Short = 999999S
          |  IO::println(x)
          |}
          |""".stripMargin
      )
      assert(result.hasErrors)
      val errors = result.allErrors
      val expected = Message("error.parsing.short_literal_out_of_range")
      assert(
        errors.exists(_.message.contains(expected)),
        s"expected a message containing '$expected', got: ${errors.map(_.message)}"
      )
    }
  }

  describe("an in-range literal with an L suffix") {
    it("still compiles (no false positive)") {
      val result = compile(
        """
          |def main: void {
          |  var x: Long = 99999999999L
          |  IO::println(x)
          |}
          |""".stripMargin
      )
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
    }
  }
}
