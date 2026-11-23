package onion.compiler.tools

import onion.tools.Shell

/** A `@TailRecursive` method of a `final` class or a record cannot be overridden, so it becomes a loop (#3256). */
class FinalOwnerTailRecursiveSpec extends AbstractShellSpec {
  describe("@TailRecursive in a class that cannot be extended (#3256)") {
    it("becomes a loop in a final class") {
      val result = shell.run(
        """
          |final class C {
          |public:
          |  def this { }
          |  @TailRecursive
          |  def sum(n: Int, acc: Long): Long = if n == 0 { acc } else { sum(n - 1, acc + n) }
          |  static def main(args: String[]): Long = new C().sum(1000000, 0L)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(500000500000L) == result)
    }

    it("becomes a loop in a record") {
      val result = shell.run(
        """
          |record R(x: Int) {
          |public:
          |  @TailRecursive
          |  def sum(n: Int, acc: Long): Long = if n == 0 { acc } else { sum(n - 1, acc + n) }
          |  static def main(args: String[]): Long = new R(1).sum(1000000, 0L)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(500000500000L) == result)
    }
  }
}
