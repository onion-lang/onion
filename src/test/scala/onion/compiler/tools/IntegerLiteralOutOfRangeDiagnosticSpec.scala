package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.compiler.parser.{JJOnionParser, OnionParser, ParseException}
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

  describe("an out-of-range literal inside a string interpolation") {
    it("is reported on the line it is written on") {
      // The panel's repro on #2261: the interpolation's line offset was added twice, so
      // this 7-line program reported line 11.
      val result = compile(
        """def main: void {
          |  val a = 1
          |  val b = 2
          |  val c = 3
          |  val d = 4
          |  println("v=#{99999999999}")
          |}
          |""".stripMargin
      )
      assert(result.hasErrors)
      val expected = Message("error.parsing.int_literal_out_of_range")
      val errors = result.allErrors.filter(_.message.contains(expected))
      assert(errors.nonEmpty, s"expected '$expected', got: ${result.allErrors.map(_.message)}")
      assert(
        errors.forall(e => e.location != null && e.location.line == 6 && e.location.column == 16),
        s"expected 6:16, got: ${errors.map(e => Option(e.location).map(l => s"${l.line}:${l.column}"))}"
      )
    }
  }

  describe("the magnitude of the minimum value, 2147483648 or 9223372036854775808L (#2296)") {
    // Both parsers accept the magnitude only so that `-2147483648` can be unary minus
    // applied to it; with no `-` right in front it used to compile to the minimum value.
    val rejected = Seq(
      "val x: Int = 2147483648"           -> "error.parsing.int_literal_out_of_range",
      "println(2147483648)"               -> "error.parsing.int_literal_out_of_range",
      "val x = 1 - 2147483648"            -> "error.parsing.int_literal_out_of_range",
      "val x = -(2147483648)"             -> "error.parsing.int_literal_out_of_range",
      "val x = 9223372036854775808L"      -> "error.parsing.long_literal_out_of_range",
      "val x = 1L - 9223372036854775808L" -> "error.parsing.long_literal_out_of_range",
      // Postfix `as`, `.m()` and `*`/`/` bind tighter than prefix `-`, so in these the
      // `-` negates something other than the bare magnitude, which had already wrapped
      // to the minimum value (the panel's repro on #2307).
      "val x = -2147483648 as Long"            -> "error.parsing.int_literal_out_of_range",
      "val x = -9223372036854775808L as Double" -> "error.parsing.long_literal_out_of_range",
      "val x = -2147483648.toString()"         -> "error.parsing.int_literal_out_of_range",
      "val x = -2147483648 / 2"                -> "error.parsing.int_literal_out_of_range",
      "val x = 3 * -2147483648.hashCode()"     -> "error.parsing.int_literal_out_of_range"
    )
    rejected.foreach { case (code, key) =>
      it(s"rejects `$code` as out of range") {
        val result = compile(code + "\n")
        val expected = Message(key)
        assert(
          result.allErrors.exists(_.message.contains(expected)),
          s"expected '$expected', got: ${result.allErrors.map(_.message)}"
        )
      }
      it(s"rejects `$code` in the JavaCC parser too") {
        assertThrows[ParseException](new JJOnionParser(new StringReader(code + "\n")).unit())
      }
    }

    val accepted = Seq(
      "val x: Int = -2147483648",
      "val x = (-2147483648) * 2",
      "val x = (-2147483648) as Long",
      "val x = - -2147483648",
      "val x = 1 + -2147483648",
      "val x = 3 * -2147483648",
      "val x: Int = 0x80000000",
      "val x: Long = -9223372036854775808L",
      "val x: Long = 2147483648L"
    )
    accepted.foreach { code =>
      it(s"still accepts `$code`, with the same AST in both parsers") {
        val result = compile(code + "\n")
        assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
        val src = code + "\n"
        val fast = try Some(OnionParser.parse(src)) catch { case _: OnionParser.Fail => None }
        fast.foreach(f => assert(f == new JJOnionParser(new StringReader(src)).unit()))
      }
    }
  }

  describe("a Byte or Short literal past its signed range (#2316)") {
    // A decimal `B`/`S` literal must fit the signed range; only the minimum's magnitude
    // (128B, 32768S) is accepted, and only as the whole operand of a `-`, as for Int. A
    // binary or hex literal may still spell the full bit pattern, as `0xFFFFFFFF` does.
    val rejected = Seq(
      "val x: Byte = 128B"           -> "error.parsing.byte_literal_out_of_range",
      "val x: Byte = 255B"           -> "error.parsing.byte_literal_out_of_range",
      "val x: Byte = -129B"          -> "error.parsing.byte_literal_out_of_range",
      "val x: Byte = 0200B"          -> "error.parsing.byte_literal_out_of_range",
      "val x: Byte = 0b111111111B"   -> "error.parsing.byte_literal_out_of_range",
      "val x: Short = 32768S"        -> "error.parsing.short_literal_out_of_range",
      "val x: Short = 40000S"        -> "error.parsing.short_literal_out_of_range",
      "val x: Short = -32769S"       -> "error.parsing.short_literal_out_of_range",
      "val x: Short = 0x10000S"      -> "error.parsing.short_literal_out_of_range",
      "val x = -128B as Int"         -> "error.parsing.byte_literal_out_of_range",
      "val x = -(128B)"              -> "error.parsing.byte_literal_out_of_range",
      "val x = -32768S.toString()"   -> "error.parsing.short_literal_out_of_range"
    )
    rejected.foreach { case (code, key) =>
      it(s"rejects `$code` as out of range") {
        val result = compile(code + "\n")
        val expected = Message(key)
        assert(
          result.allErrors.exists(_.message.contains(expected)),
          s"expected '$expected', got: ${result.allErrors.map(_.message)}"
        )
      }
      it(s"rejects `$code` in the JavaCC parser too") {
        assertThrows[ParseException](new JJOnionParser(new StringReader(code + "\n")).unit())
      }
    }

    val accepted = Seq(
      "val x: Byte = 127B",
      "val x: Byte = -128B",
      "val x: Byte = 0b11111111B",
      "val x = (-128B) as Int",
      "val x: Short = 32767S",
      "val x: Short = -32768S",
      "val x: Short = 0xFFFFS",
      "val x: Short = 0b1000000000000000S"
    )
    accepted.foreach { code =>
      it(s"still accepts `$code`, with the same AST in both parsers") {
        val result = compile(code + "\n")
        assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
        val src = code + "\n"
        val fast = try Some(OnionParser.parse(src)) catch { case _: OnionParser.Fail => None }
        fast.foreach(f => assert(f == new JJOnionParser(new StringReader(src)).unit()))
      }
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
