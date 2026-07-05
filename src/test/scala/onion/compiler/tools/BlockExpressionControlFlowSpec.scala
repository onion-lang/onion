package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression tests for issue #296: a block used as an EXPRESSION value
 * (a `val x = { ... }` block-expression, or a `select` `case P: { ... }` body)
 * failed to parse when a control-flow statement (if/while/foreach) appeared
 * before the trailing value expression.
 */
class BlockExpressionControlFlowSpec extends AbstractShellSpec {
  describe("Block-as-expression with a control-flow statement before the trailing value") {
    it("accepts an if statement before the trailing expression in a val block-expression") {
      val result = shell.run(
        """
          |class BlockExprIf {
          |public:
          |  static def main(args: String[]): Int {
          |    val y = {
          |      var x = 0
          |      if true { x = 5 }
          |      x + 5
          |    }
          |    return y
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(10) == result)
    }

    it("accepts a while statement before the trailing expression in a val block-expression") {
      val result = shell.run(
        """
          |class BlockExprWhile {
          |public:
          |  static def main(args: String[]): Int {
          |    val y = {
          |      var x = 0
          |      while x < 3 { x = x + 1 }
          |      x + 100
          |    }
          |    return y
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(103) == result)
    }

    it("accepts a foreach statement before the trailing expression in a val block-expression") {
      val result = shell.run(
        """
          |class BlockExprForeach {
          |public:
          |  static def main(args: String[]): Int {
          |    val y = {
          |      var s = 0
          |      foreach i: Int in [1, 2, 3] { s = s + i }
          |      s * 10
          |    }
          |    return y
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(60) == result)
    }

    it("accepts a control-flow statement before the trailing value in a select case body") {
      val result = shell.run(
        """
          |class SelectCaseBlock {
          |public:
          |  static def main(args: String[]): Int {
          |    val n = 2
          |    val r = select n {
          |      case 2: {
          |        var x = 0
          |        if true { x = 10 }
          |        x + 5
          |      }
          |      else: 0
          |    }
          |    return r
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(15) == result)
    }

    it("still accepts a block-expression with only simple statements") {
      val result = shell.run(
        """
          |class BlockExprSimple {
          |public:
          |  static def main(args: String[]): Int {
          |    val y = {
          |      var x = 5
          |      x + 5
          |    }
          |    return y
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(10) == result)
    }

    it("still accepts an ordinary statement-position block containing control flow") {
      val result = shell.run(
        """
          |class StatementBlock {
          |public:
          |  static def main(args: String[]): Int {
          |    var out: Int = 0
          |    {
          |      var x = 0
          |      if true { x = 5 }
          |      out = x
          |    }
          |    return out
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(5) == result)
    }
  }
}
