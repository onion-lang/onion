package onion.compiler.tools

import onion.tools.Shell

class ExpressionControlSpec extends AbstractShellSpec {
  describe("Expression control forms") {
    it("uses if expression in val assignment") {
      val result = shell.run(
        """
          |class IfExpressionSample {
          |public:
          |  static def main(args: String[]): String = {
          |    val value = if true { "ok" } else { "ng" }
          |    value
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("returns last expression in a block") {
      val result = shell.run(
        """
          |class BlockExpressionSample {
          |public:
          |  static def main(args: String[]): String = {
          |    val value = {
          |      val base = "a"
          |      base + "!"
          |    }
          |    value
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("a!") == result)
    }

    it("propagates return in an if branch") {
      val result = shell.run(
        """
          |class IfReturnSample {
          |public:
          |  static def main(args: String[]): String = {
          |    if false {
          |      return "no"
          |    } else {
          |      "yes"
          |    }
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("yes") == result)
    }
  }
}
