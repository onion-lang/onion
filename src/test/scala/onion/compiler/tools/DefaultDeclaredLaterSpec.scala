package onion.compiler.tools

import onion.tools.Shell

/**
 * A method's defaults are known to every call in its class, whatever the order of the
 * declarations: `def run() = g()` above `def g(n: Int = 5)` compiled to E0005 (#2637).
 */
class DefaultDeclaredLaterSpec extends AbstractShellSpec {
  private def run(src: String): Shell.Result = shell.run(src.stripMargin, "None", Array())

  describe("a call above the method that declares the default") {
    it("omits a default of a public method declared later") {
      val result = run(
        """
          |class A {
          |public:
          |  def this {}
          |  def run(): Int = g()
          |  def g(n: Int = 5): Int = n
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String = "" + new A().run()
          |}
        """)
      assert(Shell.Success("5") == result)
    }

    it("omits a default of a private or static method declared later, and one that reads an earlier parameter") {
      val result = run(
        """
          |class A {
          |public:
          |  def this {}
          |  def run(): String = "" + h() + "," + s() + "," + p(2)
          |private:
          |  def h(a: Int = 1): Int = a
          |  static def s(a: Int = 3): Int = a
          |  def p(a: Int, b: Int = a * 10): Int = a + b
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String = new A().run()
          |}
        """)
      assert(Shell.Success("1,3,22") == result)
    }
  }
}
