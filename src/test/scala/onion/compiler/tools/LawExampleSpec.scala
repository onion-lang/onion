package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * B3: `law` / `example` clauses on a record are executed by the compiler at build time
 * (LawCheckPhase). `example { e }` must evaluate true; `law name(p: T) { e }` must hold for
 * every generated sample of p. A false example or a falsified law fails compilation —
 * the spec asserts Shell.Success (compiled + ran) vs Shell.Failure (compile error), and for
 * the error-code-bearing cases also asserts the specific E0064/E0065/E0074 diagnostic code
 * (LawCheckPhase), since a bare Shell.Failure doesn't distinguish these from an unrelated
 * compile error.
 */
class LawExampleSpec extends AbstractShellSpec {
  private def errorCodes(program: String): Seq[Option[String]] =
    errors(program).map(_.errorCode)

  private def errors(program: String): Seq[onion.compiler.CompileError] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val inputs = Seq(new StreamInputSource(() => new StringReader(program), "LawExample.on"))
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Failure(errs) => errs
      case _ => Seq.empty
    }
  }

  describe("law / example compile-time checks") {
    it("compiles and runs when law and example hold") {
      val result = shell.run(
        """
          |record Pt(x: Int, y: Int) from re"(-?\d+),(-?\d+)" {
          |  law roundtrip(p: Pt) { Pt::parse(Pt::format(p)) == p }
          |  example { Pt::parse("3,4") == new Pt(3, 4) }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("fails compilation on a false example") {
      val result = shell.run(
        """
          |record R(x: Int) {
          |  example { new R(1).x() == 2 }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("reports E0065 for a false example") {
      val codes = errorCodes(
        """
          |record R(x: Int) {
          |  example { new R(1).x() == 2 }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin)
      assert(codes.contains(Some("E0065")), s"expected E0065, got: $codes")
    }

    it("fails compilation on a falsified law (counterexample x != y)") {
      val result = shell.run(
        """
          |record Pt(x: Int, y: Int) {
          |  law wrong(p: Pt) { p.x() == p.y() }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("reports E0064 for a falsified law (counterexample x != y)") {
      val codes = errorCodes(
        """
          |record Pt(x: Int, y: Int) {
          |  law wrong(p: Pt) { p.x() == p.y() }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin)
      assert(codes.contains(Some("E0064")), s"expected E0064, got: $codes")
    }

    it("reports E0074 for a law parameter type that cannot be sample-generated") {
      val codes = errorCodes(
        """
          |record Dummy(v: Int) {
          |  law bad(r: Runnable) { r != null }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin)
      assert(codes.contains(Some("E0074")), s"expected E0074, got: $codes")
    }

    it("fails compilation on a falsified scalar-arg law (generator produces negatives)") {
      val result = shell.run(
        """
          |record Dummy(v: Int) {
          |  law nonneg(n: Int) { n >= 0 }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("passes a true scalar-arg law") {
      val result = shell.run(
        """
          |record Dummy(v: Int) {
          |  law reflexive(n: Int) { n == n }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("coexists with derive!(Json) — law holds, json round-trips") {
      val result = shell.run(
        """
          |record P(x: Int, y: Int) from re"(-?\d+),(-?\d+)" derive!(Json) {
          |  law textRoundtrip(p: P) { P::parse(P::format(p)) == p }
          |  example { P::fromJson(P::toJson(new P(3, 4))) == new P(3, 4) }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }
  }

  // #2513: the String samples never contained a delimiter or a control character, so a
  // round-trip law over a comma-, tab- or newline-delimited shape "held" for a shape that
  // loses data on ordinary input.
  describe("String samples exercise delimiters and control characters (#2513)") {
    def lawProgram(regex: String): String =
      s"""
         |record Row(name: String, n: Int) from re"$regex" {
         |  law roundtrip(r: Row) { Row::parse(Row::format(r)) == r }
         |}
         |class Test {
         |public:
         |  static def main(args: String[]): String { return "ok" }
         |}
         |""".stripMargin

    it("falsifies a round-trip law over a comma-delimited shape") {
      val codes = errorCodes(lawProgram("""([^,]*),(-?\d+)"""))
      assert(codes == Seq(Some("E0064")), s"expected only E0064, got: $codes")
    }

    it("samples a String containing a tab") {
      val codes = errorCodes(
        """
          |record Cell(v: String) {
          |  law noTab(s: String) { s.indexOf("\t") < 0 }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin)
      assert(codes == Seq(Some("E0064")), s"expected only E0064, got: $codes")
    }

    it("falsifies a round-trip law whose string group cannot hold a newline") {
      val codes = errorCodes(lawProgram("""(.*):(-?\d+)"""))
      assert(codes == Seq(Some("E0064")), s"expected only E0064, got: $codes")
    }

    it("still passes a round-trip law that holds for every String") {
      val codes = errorCodes(
        """
          |record Name(v: String) {
          |  law same(s: String) { new Name(s).v() == s }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "ok" }
          |}
          |""".stripMargin)
      assert(codes.isEmpty, s"expected no errors, got: $codes")
    }
  }

  // #2519 review: the samples above include a newline and a tab. Printed raw, a newline
  // split the E0064 diagnostic over two lines, a tab showed as blank space, and
  // `Row(name=,, n=1)` did not show where `name` ends. A String is shown as an Onion
  // string literal, so the counterexample can be pasted into an `example` clause.
  describe("counterexamples show Strings as Onion string literals (#2513)") {
    def lawMessage(program: String, code: String = "E0064"): String = {
      val found = errors(program)
      found.find(_.errorCode.contains(code)).map(_.message)
        .getOrElse(fail(s"expected $code, got: ${found.map(e => e.errorCode -> e.message)}"))
    }

    def program(body: String): String =
      s"""
         |$body
         |class Test {
         |public:
         |  static def main(args: String[]): String { return "ok" }
         |}
         |""".stripMargin

    it("quotes and escapes a String record component holding a newline") {
      val msg = lawMessage(program(
        """record Row(name: String, n: Int) from re"(.*):(-?\d+)" {
          |  law roundtrip(r: Row) { Row::parse(Row::format(r)) == r }
          |}""".stripMargin))
      assert(!msg.contains("\n"), s"raw newline in: $msg")
      assert(msg.contains("Row(name=\"\\n\", n="), msg)
    }

    it("quotes a String parameter, so a tab is visible") {
      val msg = lawMessage(program(
        """record Cell(v: String) {
          |  law noTab(s: String) { s.indexOf("\t") < 0 }
          |}""".stripMargin))
      assert(!msg.contains("\t"), s"raw tab in: $msg")
      assert(msg.contains("(\"\\t\")"), msg)
    }

    it("quotes a String holding a comma, so it reads as one argument") {
      val msg = lawMessage(program(
        """record Cell(v: String) {
          |  law noComma(s: String, n: Int) { s.indexOf(",") < 0 }
          |}""".stripMargin))
      assert(msg.contains("(\",\", "), msg)
    }

    it("keeps a law that threw on one line, cause included") {
      val msg = lawMessage(program(
        """record Cell(v: String) {
          |  law digits(s: String) { if s.indexOf("\n") >= 0 { Integer::parseInt(s) == 0 } else { true } }
          |}""".stripMargin))
      assert(!msg.contains("\n"), s"raw newline in: $msg")
      assert(msg.contains("(\"\\n\")"), msg)
      assert(msg.contains("NumberFormatException"), msg)
    }

    it("leaves a record with its own toString to that toString") {
      val msg = lawMessage(program(
        """record Tag(v: String) {
          |  law never(t: Tag) { t.v() == "a" }
          |public:
          |  override def toString(): String = "Tag<" + v() + ">"
          |}""".stripMargin))
      assert(msg.contains("(Tag<>)"), msg)
    }

    it("prints a literal that reads back as the same String") {
      val samples = Seq("\n", "\r\n", "\t", "\"", "\\", "#{x}", "a\u0001b", "\u007f", "日本語", "\\u0041", "\\n", "x#y")
      for (s <- samples) {
        val literal = onion.compiler.verification.CounterexampleText.stringLiteral(s)
        assert(!literal.exists(c => c < ' ' || c == 0x7f), s"control character left in $literal")
        val result = shell.run(
          s"""
             |class Test {
             |public:
             |  static def main(args: String[]): String { return $literal }
             |}
             |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success(s) == result, s"$literal read back as $result")
      }
    }
  }
}
