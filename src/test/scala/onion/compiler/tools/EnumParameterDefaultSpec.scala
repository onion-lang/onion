package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell
import java.io.StringReader

/**
 * Issue #2948: a data enum's parameter may declare a default, and a constant that omits the
 * argument gets it, as a record or constructor call does. The default parsed, then every
 * constant that omitted it was E0021 naming the hidden `E(String, Int, Int, Int)`.
 */
class EnumParameterDefaultSpec extends AbstractShellSpec {

  private def runMain(source: String, name: String): Shell.Result =
    shell.run(source, name, Array())

  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def enumProgram(decl: String, body: String): String =
    s"""
       |$decl
       |class Test {
       |public:
       |  static def main(args: String[]): String { return $body }
       |}
       |""".stripMargin

  describe("Data-enum parameter defaults (#2948)") {
    it("fills an omitted trailing argument from its default") {
      val result = runMain(
        """
          |enum E(a: Int, b: Int = 7) { X(1), Y(2, 3) }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + E::X.a() + E::X.b() + "," + E::Y.a() + E::Y.b()
          |  }
          |}
          |""".stripMargin,
        "EnumDefaultBasic.on"
      )
      assert(Shell.Success("17,23") == result)
    }

    it("fills several defaults, and a default can read an earlier parameter") {
      val result = runMain(
        """
          |enum Size(w: Int, h: Int = w * 2, label: String = "s" + w) {
          |  SMALL(1), WIDE(2, 1), NAMED(3, 3, "n")
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var r = ""
          |    foreach s: Size in Size::values() { r = r + s.w() + "x" + s.h() + ":" + s.label() + ";" }
          |    return r
          |  }
          |}
          |""".stripMargin,
        "EnumDefaultSeveral.on"
      )
      assert(Shell.Success("1x2:s1;2x1:s2;3x3:n;") == result)
    }

    it("evaluates a default once per constant that omits it, in declaration order") {
      val result = runMain(
        """
          |class Counter {
          |public:
          |  static var n: Int = 0
          |  static def next(): Int { Counter::n = Counter::n + 1; return Counter::n }
          |}
          |enum E(a: Int, c: Int = Counter::next()) { X(1), Y(2, 9), Z(3) }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + E::X.c() + E::Y.c() + E::Z.c()
          |  }
          |}
          |""".stripMargin,
        "EnumDefaultOnce.on"
      )
      assert(Shell.Success("192") == result)
    }

    it("still rejects a constant that omits a parameter with no default") {
      val result = runMain(
        """
          |enum E(a: Int, b: Int, c: Int = 1) { X(1) }
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "no" }
          |}
          |""".stripMargin,
        "EnumDefaultMissing.on"
      )
      assert(Shell.Failure(-1) == result)
    }

    it("reports a default whose type does not fit its parameter") {
      val result = runMain(
        """
          |enum E(a: Int, b: Int = "seven") { X(1) }
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "no" }
          |}
          |""".stripMargin,
        "EnumDefaultBadType.on"
      )
      assert(Shell.Failure(-1) == result)
    }
  }

  describe("Named arguments in a data-enum constant (#3062, #3064)") {
    it("binds a named argument by name and defaults the parameter it skips, as a record does") {
      val src = enumProgram(
        "enum E(a: Int, b: Int = 7, c: Int = 9) { X(1, c = 2) }",
        "\"\" + E::X.a() + \",\" + E::X.b() + \",\" + E::X.c()"
      )
      assert(Shell.Success("1,7,2") == runMain(src, "EnumNamedSkip.on"))
    }

    it("binds named arguments written out of order") {
      val src = enumProgram(
        "enum S(a: Int, b: Int) { Y(b = 5, a = 2) }",
        "\"\" + S::Y.a() + \" \" + S::Y.b()"
      )
      assert(Shell.Success("2 5") == runMain(src, "EnumNamedOrder.on"))
    }

    it("lets a default read an earlier parameter given by name") {
      val src = enumProgram(
        "enum S(a: Int, b: Int = a + 1) { Z(a = 4) }",
        "\"\" + S::Z.a() + \" \" + S::Z.b()"
      )
      assert(Shell.Success("4 5") == runMain(src, "EnumNamedDefaultReads.on"))
    }

    it("rejects a constant that names only a defaulted parameter and omits a required one") {
      val src = enumProgram("enum S(a: Int, b: Int = a + 1) { Z(b = 9) }", "\"no\"")
      assert(errorCodes(src) == Seq("E0021"))
    }

    it("rejects an unknown parameter name (E0043)") {
      val src = enumProgram("enum E(a: Int, b: Int = 7) { X(1, zz = 2) }", "\"no\"")
      assert(errorCodes(src) == Seq("E0043"))
    }

    it("rejects a parameter given twice (E0044)") {
      val src = enumProgram("enum E(a: Int, b: Int = 7) { X(1, a = 2) }", "\"no\"")
      assert(errorCodes(src) == Seq("E0044"))
    }

    it("rejects a positional argument after a named one (E0045)") {
      val src = enumProgram("enum E(a: Int, b: Int) { X(a = 1, 2) }", "\"no\"")
      assert(errorCodes(src) == Seq("E0045"))
    }

    it("still type-checks a named argument against its parameter") {
      val src = enumProgram("enum E(a: Int, b: String) { X(b = 3, a = 1) }", "\"no\"")
      assert(errorCodes(src).nonEmpty)
    }
  }
}
