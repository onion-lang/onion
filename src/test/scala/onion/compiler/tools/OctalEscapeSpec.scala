package onion.compiler.tools

import onion.tools.Shell

/** #2529: `"\101"` is `"A"`, as in Java, instead of the digits left in the string. */
class OctalEscapeSpec extends AbstractShellSpec {
  private def eval(expr: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |    return $expr
         |  }
         |}
         |""".stripMargin,
      "OctalEscape.on",
      Array()
    )

  describe("octal escapes") {
    it("decode in a string literal") {
      assert(Shell.Success("A") == eval("\"\\101\""))
    }
    it("take at most three digits, and two when the first is 4-7") {
      assert(Shell.Success("A1") == eval("\"\\1011\""))
      assert(Shell.Success("!8") == eval("\"\\418\""))
      assert(Shell.Success("!1") == eval("\"\\411\""))
    }
    it("work in an interpolated string and keep a short escape") {
      assert(Shell.Success("3 A") == eval("\"#{1 + 2} \\101\""))
      assert(Shell.Success("3") == eval("\"a\\001b\".length() + \"\""))
    }
    it("decode in a character literal") {
      assert(Shell.Success("A") == eval("'\\101' + \"\""))
    }
  }
}
