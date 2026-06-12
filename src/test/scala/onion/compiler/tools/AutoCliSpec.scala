package onion.compiler.tools

import onion.tools.Shell

/**
 * Auto-CLI: a top-level `def main(p1: T1, p2: T2 = default, ...)` whose
 * parameters are CLI-convertible gets a synthesized call that parses `args`
 * via onion.Cli — required params positional, defaulted params --name flags
 * (Boolean defaults become switches), defaults evaluated in-language.
 */
class AutoCliSpec extends AbstractShellSpec {
  describe("auto-CLI from the main signature") {
    it("binds positional args and applies defaults") {
      val result = shell.run(
        """
          |var captured = ""
          |def main(name: String, count: Int = 3): void {
          |  captured = name + ":" + count
          |}
          |IO::println(captured)
          |""".stripMargin,
        "None",
        Array("world")
      )
      assert(result.isInstanceOf[Shell.Success])
    }

    it("converts flag values to the declared types") {
      val result = shell.run(
        """
          |def main(name: String, count: Int = 3, loud: Boolean = false): String {
          |  var msg = name + " x" + count
          |  if loud { msg = msg.toUpperCase() }
          |  IO::println(msg)
          |  return msg
          |}
          |""".stripMargin,
        "None",
        Array("hi", "--count", "5", "--loud")
      )
      assert(result.isInstanceOf[Shell.Success])
    }

    it("does not fire for a conventional main(String[])") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int = args.length
          |}
          |""".stripMargin,
        "None",
        Array("a", "b")
      )
      assert(Shell.Success(2) == result)
    }
  }
}
