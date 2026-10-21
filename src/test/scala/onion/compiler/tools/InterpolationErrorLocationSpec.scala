package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.compiler.toolbox.Message
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Regression test (#2303, #2302): a syntax error inside a string interpolation is reported
 * at the line and column it is written on. Each `#{}` level parses its body with a parser
 * whose positions are offset into the enclosing file; a failure position that was already
 * offset was offset again, once per nesting level (or once for the no-arrow trailing-lambda
 * case), so a 6-line file reported line 11 and lost its source excerpt.
 */
class InterpolationErrorLocationSpec extends AnyFunSpec {

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def compile(code: String) =
    new OnionCompiler(newConfig).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )

  private def locations(code: String): Seq[String] = {
    val result = compile(code)
    assert(result.hasErrors, "expected a compile error")
    result.allErrors.map(e => Option(e.location).map(l => s"${l.line}:${l.column}").getOrElse("none"))
  }

  private val fivePrefixLines = "\n\n\n\n\n"

  describe("a syntax error inside a nested #{}") {
    it("is reported on the line it is written on (two levels)") {
      // The inner body `1 +` ends early; the error sits on its `+`, column 25 of line 6.
      val locs = locations(fivePrefixLines + "IO::println(\"a #{\"b #{1 +} c\"} d\")\n")
      assert(locs == Seq("6:25"), s"got $locs")
    }

    it("is reported on the line it is written on (three levels)") {
      val locs = locations(fivePrefixLines + "IO::println(\"a #{\"b #{\"c #{1 +} d\"} e\"} f\")\n")
      assert(locs == Seq("6:30"), s"got $locs")
    }

    it("keeps the column right on line 1") {
      val locs = locations("IO::println(\"a #{\"b #{1 +} c\"} d\")\n")
      assert(locs == Seq("1:25"), s"got $locs")
    }

    it("reports an out-of-range literal on its own line and column") {
      val result = compile(fivePrefixLines + "IO::println(\"a #{\"b #{99999999999} c\"} d\")\n")
      val expected = Message("error.parsing.int_literal_out_of_range")
      val errors = result.allErrors.filter(_.message.contains(expected))
      assert(errors.nonEmpty, s"expected '$expected', got: ${result.allErrors.map(_.message)}")
      val locs = errors.map(e => Option(e.location).map(l => s"${l.line}:${l.column}").getOrElse("none"))
      assert(locs.forall(_ == "6:23"), s"got $locs")
    }

    def minValueLocations(code: String): Seq[String] = {
      val result = compile(code)
      val expected = Message("error.parsing.int_literal_out_of_range")
      val errors = result.allErrors.filter(_.message.contains(expected))
      assert(errors.nonEmpty, s"expected '$expected', got: ${result.allErrors.map(_.message)}")
      errors.map(e => Option(e.location).map(l => s"${l.line}:${l.column}").getOrElse("none"))
    }

    it("reports a minimum-value magnitude that is not the whole operand of `-` on its own line (one level)") {
      val locs = minValueLocations(fivePrefixLines + "IO::println(\"a #{-2147483648 as Long} d\")\n")
      assert(locs.forall(_ == "6:19"), s"got $locs")
    }

    it("reports a minimum-value magnitude that is not the whole operand of `-` on its own line (two levels)") {
      val locs = minValueLocations(fivePrefixLines + "IO::println(\"a #{\"b #{-2147483648 as Long} c\"} d\")\n")
      assert(locs.forall(_ == "6:24"), s"got $locs")
    }
  }

  describe("a parenthesized trailing-lambda head inside #{}") {
    it("is reported at its `{`, with the same hint as outside an interpolation") {
      val code =
        """def main: void {
          |  val xs = [1, 2]
          |  val b = 2
          |  val c = 3
          |  val d = 4
          |  println("v=#{xs.map { (x) -> x }}")
          |}
          |""".stripMargin
      val outside =
        """def main: void {
          |  val xs = [1, 2]
          |  println(xs.map { (x) -> x })
          |}
          |""".stripMargin
      val inner = compile(code)
      val outer = compile(outside)
      assert(inner.hasErrors && outer.hasErrors)
      val locs = inner.allErrors.map(e => Option(e.location).map(l => s"${l.line}:${l.column}").getOrElse("none"))
      assert(locs == Seq("6:23"), s"got $locs")
      // Outside, recovery also reports the stray `)` that follows; the first error is the one
      // that must match.
      assert(inner.allErrors.head.message == outer.allErrors.head.message,
        s"inside: ${inner.allErrors.map(_.message)}\noutside: ${outer.allErrors.map(_.message)}")
    }
  }
}
