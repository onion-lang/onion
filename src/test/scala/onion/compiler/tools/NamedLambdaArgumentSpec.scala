package onion.compiler.tools

import onion.tools.Shell

class NamedLambdaArgumentSpec extends AbstractShellSpec {
  describe("an untyped lambda passed as a named argument (#2799)") {
    it("takes its parameter type from the named parameter") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def ap(f: (String) -> String, s: String): String { return f(s); }
          |  static def main(args: String[]): String {
          |    return ap(s = "S", f = (x) -> x + "!");
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("S!") == result)
    }

    it("works on an instance method too") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  def ap(f: (String) -> String, s: String): String { return f(s); }
          |  static def main(args: String[]): String {
          |    return new Test().ap(f = (x) -> x + "?", s = "Q");
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Q?") == result)
    }
  }
}
