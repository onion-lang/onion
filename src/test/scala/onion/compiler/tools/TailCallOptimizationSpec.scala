package onion.compiler.tools

import onion.tools.Shell

/**
 * Tail-call optimization rewrites direct self-recursion in non-overridable
 * methods (private, static, or final) into a loop. A depth of 100000 would
 * overflow the stack without it.
 */
class TailCallOptimizationSpec extends AbstractShellSpec {
  describe("tail-call optimization") {
    it("optimizes a static method (no stack overflow at depth 100000)") {
      val result = shell.run(
        """
          |class C {
          |public:
          |  static def count(n: Int, acc: Int): Int {
          |    if n == 0 { return acc }
          |    return count(n - 1, acc + 1)
          |  }
          |  static def main(args: String[]): Int = count(100000, 0)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(100000) == result)
    }

    it("optimizes a top-level function") {
      val result = shell.run(
        """
          |def count(n: Int, acc: Int): Int {
          |  if n == 0 { return acc }
          |  return count(n - 1, acc + 1)
          |}
          |IO::println(count(100000, 0))
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }
  }
}
