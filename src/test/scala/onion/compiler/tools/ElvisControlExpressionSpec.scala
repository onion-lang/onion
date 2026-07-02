package onion.compiler.tools

import onion.tools.Shell

/**
 * The elvis operator's right-hand side may be a control expression
 * (`throw` / `return`), so a null-check early-exit fits on one line.
 */
class ElvisControlExpressionSpec extends AbstractShellSpec {
  describe("elvis with a control-expression right-hand side") {
    it("keeps the value when the left is non-null (throw RHS)") {
      val r = shell.run(
        """
          |def firstOr(xs: List[String]): String {
          |  val x: String? = xs.get(0)
          |  return x ?: throw new RuntimeException("empty")
          |}
          |def main(args: String[]): String { return firstOr(["hi"]) }
          |""".stripMargin, "None", Array())
      assert(Shell.Success("hi") == r)
    }
    it("throws when the left is null (throw RHS)") {
      val r = shell.run(
        """
          |def main(args: String[]): Int {
          |  val s: String? = null
          |  var caught: Int = 0
          |  try { val v: String = s ?: throw new RuntimeException("nil") } catch e: Exception { caught = 1 }
          |  return caught
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("allows return as the RHS") {
      val r = shell.run(
        """
          |def firstOrEmpty(xs: List[String]): String {
          |  val x: String? = if xs.size() > 0 { xs.get(0) } else { null }
          |  return x ?: return "default"
          |}
          |def main(args: String[]): String { return firstOrEmpty([]) }
          |""".stripMargin, "None", Array())
      assert(Shell.Success("default") == r)
    }
    it("still works for an ordinary value RHS") {
      val r = shell.run("def main(args: String[]): Int { val n: Int? = null\n return n ?: -1 }", "None", Array())
      assert(Shell.Success(-1) == r)
    }
  }
}
