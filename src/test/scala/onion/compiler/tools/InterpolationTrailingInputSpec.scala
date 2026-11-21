package onion.compiler.tools

import onion.tools.Shell

/** `#{...}` holds exactly one term: leftover input is a syntax error, not silently dropped (#3166). */
class InterpolationTrailingInputSpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): String = $body
         |}
        """.stripMargin,
      "None",
      Array()
    )

  describe("trailing input inside an interpolation") {
    it("rejects a call applied to a parenthesised term") {
      assert(run("\"#{ (1)(2) }\"") == Shell.Failure(-1))
    }
    it("rejects a call applied to a call result") {
      assert(run("\"a#{ Integer::valueOf(1)(2) }b\"") == Shell.Failure(-1))
    }
    it("rejects leftovers in a multi-line string") {
      assert(run("\"\"\"a#{ (1)(2) }b\"\"\"") == Shell.Failure(-1))
    }
    it("still accepts a parenthesised term, a call and surrounding blanks") {
      assert(run("\"#{ (1) + 2 }|#{ Integer::toString(7) }|#{ 4 }\"") == Shell.Success("3|7|4"))
    }
  }
}
