package onion.compiler.tools

import onion.tools.Shell

/**
 * Constructors overloaded only by the arity of their function parameter are picked
 * by the lambda's parameter count, as overloaded methods are (#2917).
 */
class ConstructorLambdaArityOverloadSpec extends AbstractShellSpec {
  private def run(src: String) = shell.run(src, "None", Array())

  private val header =
    """
      |class A {
      |  val tag: String
      |public:
      |  def this(f: (Int) -> Int) { this.tag = "one" + f(1) }
      |  def this(f: (Int, Int) -> Int) { this.tag = "two" + f(1, 2) }
      |  def show(): String = tag
      |}
      |""".stripMargin

  describe("an untyped lambda against constructors overloaded by lambda arity") {
    it("picks the two-parameter constructor for a two-parameter lambda") {
      assert(Shell.Success("two3") == run(header +
        """
          |def main(args: String[]): String { return (new A((x, y) -> x + y)).show() }
          |""".stripMargin))
    }
    it("picks the one-parameter constructor for a one-parameter lambda") {
      assert(Shell.Success("one11") == run(header +
        """
          |def main(args: String[]): String { return (new A((x) -> x + 10)).show() }
          |""".stripMargin))
    }
    it("stays an error when no constructor takes that many parameters") {
      assert(Shell.Failure(-1) == run(header +
        """
          |def main(args: String[]): String { return (new A((x, y, z) -> x)).show() }
          |""".stripMargin))
    }
  }
}
