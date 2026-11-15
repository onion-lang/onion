package onion.compiler.tools

import onion.tools.Shell

/**
 * A block lambda with an early `return <value>` also returns its last expression.
 * It used to evaluate that expression and return null instead (#3025).
 */
class ClosureTrailingValueSpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): Int {
         |$body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("a block lambda with an early return") {
    it("returns its last expression on the other path (Int)") {
      val result = run(
        """    val n: (Boolean) -> Int = (c) -> {
          |      if c { return 5 }
          |      40
          |    }
          |    return n(false) + n(true) * 100""".stripMargin)
      assert(Shell.Success(540) == result)
    }

    it("returns its last expression on the other path (String)") {
      val result = run(
        """    val s: (Boolean) -> String = (c) -> {
          |      if c { return "early" }
          |      "late"
          |    }
          |    return s(false).length() * 10 + s(true).length()""".stripMargin)
      assert(Shell.Success(45) == result)
    }

    it("still ends in a throw") {
      val result = run(
        """    val f: (Boolean) -> Int = (c) -> {
          |      if c { return 7 }
          |      throw new Exception("no")
          |    }
          |    return f(true)""".stripMargin)
      assert(Shell.Success(7) == result)
    }
  }
}
