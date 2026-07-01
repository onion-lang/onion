package onion.compiler.tools

import onion.tools.Shell

/**
 * An invalid `re"..."` literal must be rejected at compile time (E0059) in any
 * position, matching `case re"..."` / `from re"..."`. Previously an invalid
 * pattern in a val/expression position threw a raw PatternSyntaxException at run
 * time (with internal compiler frames in the trace).
 */
class RegexLiteralValidationSpec extends AbstractShellSpec {
  describe("regex literal validation") {
    it("rejects an invalid re\"...\" in a val binding at compile time") {
      val r = shell.run("val p = re\"(unclosed\"\nIO::println(\"x\")", "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("rejects an invalid re\"...\" passed as an argument") {
      val r = shell.run("IO::println(re\"[a-\")", "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("accepts a valid re\"...\" literal") {
      val r = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val p = re"\d+-\d+"
          |    return p.matcher("12-34").matches()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(true) == r)
    }
  }
}
