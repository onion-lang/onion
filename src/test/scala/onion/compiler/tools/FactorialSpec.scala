package onion.compiler.tools

import onion.tools.Shell

class FactorialSpec extends AbstractShellSpec {
  describe("Factorial class") {
    it("shows result of 5!") {
      val resultFac5 = shell.run(
        """
          |class Factorial {
          |  static def factorial(n: Int): Int {
          |    if n < 2 { return 1; } else { return n * factorial(n - 1); }
          |  }
          |public:
          |  static def main(args: String[]): Int {
          |    return factorial(5);
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      def factorial(n: Int): Int = if(n < 2) 1 else n * factorial(n - 1)
      val answer = factorial(5)
      assert(Shell.Success(answer) == resultFac5)
    }
  }
}