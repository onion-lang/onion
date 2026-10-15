package onion.compiler.tools

import onion.tools.Shell

/**
 * A lambda with the wrong number of parameters for its target functional
 * interface is a compile error, not a VerifyError or a silent no-op (#2069).
 */
class LambdaArityMismatchSpec extends AbstractShellSpec {
  private def run(src: String) = shell.run(src, "None", Array())

  describe("lambda arity against a functional interface") {
    it("rejects a 1-param lambda for Comparator (2 params)") {
      assert(Shell.Failure(-1) == run(
        """
          |val c: Comparator[Int] = (a) -> a
          |""".stripMargin))
    }
    it("rejects a 2-param lambda for Function1") {
      assert(Shell.Failure(-1) == run(
        """
          |val c: Function1[Int, Int] = (a, b) -> a
          |""".stripMargin))
    }
    it("rejects a sort-by-key lambda passed to List.sort") {
      assert(Shell.Failure(-1) == run(
        """
          |val xs = [3, 1, 2]
          |xs.sort { i -> i }
          |""".stripMargin))
    }
    it("still accepts the matching arity") {
      assert(Shell.Success("ok") == run(
        """
          |def main(args: String[]): String {
          |  val xs = [3, 1, 2]
          |  xs.sort { a, b -> a - b }
          |  val c: Comparator[Int] = (a, b) -> a - b
          |  return "ok"
          |}
          |""".stripMargin))
    }
  }
}
