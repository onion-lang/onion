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
  private def errorCodes(program: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val inputs = Seq(new StreamInputSource(() => new StringReader(program), "LawExample.on"))
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("law / example compile-time checks") {
    it("compiles and runs when law and example hold") {
      val result = shell.run(
        """
          |record Pt(x: Int, y: Int) from re"(-?\d+),(-?\d+)"
          |  law roundtrip(p: Pt) { Pt::parse(Pt::format(p)) == p }
          |  example { Pt::parse("3,4") == new Pt(3, 4) }
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
          |record R(x: Int)
          |  example { new R(1).x() == 2 }
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
          |record R(x: Int)
          |  example { new R(1).x() == 2 }
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
          |record Pt(x: Int, y: Int)
          |  law wrong(p: Pt) { p.x() == p.y() }
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
          |record Pt(x: Int, y: Int)
          |  law wrong(p: Pt) { p.x() == p.y() }
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
          |record Dummy(v: Int)
          |  law bad(r: Runnable) { r != null }
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
          |record Dummy(v: Int)
          |  law nonneg(n: Int) { n >= 0 }
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
          |record Dummy(v: Int)
          |  law reflexive(n: Int) { n == n }
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
          |record P(x: Int, y: Int) from re"(-?\d+),(-?\d+)" derive!(Json)
          |  law textRoundtrip(p: P) { P::parse(P::format(p)) == p }
          |  example { P::fromJson(P::toJson(new P(3, 4))) == new P(3, 4) }
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
}
