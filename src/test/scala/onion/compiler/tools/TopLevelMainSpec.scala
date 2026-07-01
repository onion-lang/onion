package onion.compiler.tools

import onion.tools.Shell

/**
 * A user-defined top-level `def main(args: String[])` must be used as the entry
 * point, not collide with the synthesized main (previously E0010).
 */
class TopLevelMainSpec extends AbstractShellSpec {
  describe("top-level def main") {
    it("is used as the entry point") {
      val r = shell.run("def main(args: String[]): Int { return 7 }", "None", Array())
      assert(Shell.Success(7) == r)
    }
    it("receives the program arguments") {
      val r = shell.run(
        """
          |def main(args: String[]): Int {
          |  var total: Int = 0
          |  foreach a: String in args { total = total + a.length() }
          |  return total
          |}
          |""".stripMargin, "None", Array("aa", "bbb"))
      assert(Shell.Success(5) == r)
    }
    it("does not break plain top-level scripts") {
      // No user main: the synthesized void main runs the top-level statements.
      val r = shell.run("IO::println(\"x\")\n1 + 2", "None", Array())
      assert(Shell.Success(null) == r)
    }
  }
}
