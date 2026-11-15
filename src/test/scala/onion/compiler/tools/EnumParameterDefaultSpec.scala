package onion.compiler.tools

import onion.tools.Shell

/**
 * Issue #2948: a data enum's parameter may declare a default, and a constant that omits the
 * argument gets it, as a record or constructor call does. The default parsed, then every
 * constant that omitted it was E0021 naming the hidden `E(String, Int, Int, Int)`.
 */
class EnumParameterDefaultSpec extends AbstractShellSpec {

  private def runMain(source: String, name: String): Shell.Result =
    shell.run(source, name, Array())

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
}
