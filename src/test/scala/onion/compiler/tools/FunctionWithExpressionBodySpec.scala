package onion.compiler.tools

import onion.tools.Shell

class FunctionWithExpressionBodySpec extends AbstractShellSpec {
  describe("ExpressionBody class") {
    it("returns String a value") {
      val resultExpressionBody = shell.run(
        """
          |class ExpressionBody {
          |public:
          |  static def main(args: String[]): String = "ExpressionBody"
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ExpressionBody") == resultExpressionBody)
    }
  }
}