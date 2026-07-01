package onion.compiler.tools

import onion.tools.Shell

class SyntaxErrorHintsSpec extends AbstractShellSpec {
  describe("Syntax error hints") {

    it("rejects `for x in xs` syntax") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs = [1, 2, 3]
          |    for x in xs {
          |      println(x)
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin,
        "ForInHint.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }

    it("rejects `if` used as an expression") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = if (true) 1 else 2
          |    return x
          |  }
          |}
          |""".stripMargin,
        "IfExpressionHint.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }
  }
}
