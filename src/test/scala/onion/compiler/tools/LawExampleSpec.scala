package onion.compiler.tools

import onion.tools.Shell

/**
 * B3: `law` / `example` clauses on a record are executed by the compiler at build time
 * (LawCheckPhase). `example { e }` must evaluate true; `law name(p: T) { e }` must hold for
 * every generated sample of p. A false example or a falsified law fails compilation —
 * the spec asserts Shell.Success (compiled + ran) vs Shell.Failure (compile error).
 */
class LawExampleSpec extends AbstractShellSpec {
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
