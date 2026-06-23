package onion.compiler.tools

import onion.tools.Shell

/**
 * Top-level functions and val/var become static members of the synthetic
 * top-level class, so a method of ANY class can call a top-level function by
 * bare name (and reach top-level val/var). Previously this raised E0005.
 */
class TopLevelStaticCallSpec extends AbstractShellSpec {
  describe("top-level static resolution from other classes") {
    it("lets a class method call a bare top-level function (value checked)") {
      val result = shell.run(
        """
          |def helper(n: Int): Int = n * 2
          |class C {
          |public:
          |  def use(): Int { return helper(21) }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String { return "" + new C().use() }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("lets a class method call a top-level fn that reads a top-level val") {
      val result = shell.run(
        """
          |val base = 100
          |def withBase(n: Int): Int = base + n
          |class D {
          |public:
          |  def go(): Int { return withBase(23) }
          |}
          |IO::println(new D().go())
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }

    it("lets a class method reassign a top-level var") {
      val result = shell.run(
        """
          |var counter = 0
          |def bump(): void { counter = counter + 1 }
          |class E {
          |public:
          |  def tick(): void { bump() }
          |}
          |new E().tick()
          |new E().tick()
          |IO::println(counter)
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }
  }
}
