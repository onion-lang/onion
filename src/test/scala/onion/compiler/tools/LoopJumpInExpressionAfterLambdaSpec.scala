package onion.compiler.tools

import onion.tools.Shell

/**
 * A `continue` or `break` inside an expression (`val y = if c { continue } else { i }`)
 * must still find its loop when a lambda was compiled earlier in the same method (#2913).
 * It crashed the compiler with I0000 "Continue statement outside of loop".
 */
class LoopJumpInExpressionAfterLambdaSpec extends AbstractShellSpec {
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

  describe("a loop jump inside an expression, after a lambda in the same method") {
    it("continue in an if expression bound by val") {
      val result = run(
        """    val f: Int -> Int = (a) -> a + 1
          |    var s: Int = 0
          |    var i: Int = 0
          |    while i < 4 {
          |      i = i + 1
          |      val y = if i == 2 { continue } else { i }
          |      s = s + y
          |    }
          |    return s * 10 + f(0)""".stripMargin)
      assert(Shell.Success(81) == result)
    }

    it("break in an if expression bound by val") {
      val result = run(
        """    val f: Int -> Int = (a) -> a + 1
          |    var s: Int = 0
          |    var i: Int = 0
          |    while i < 4 {
          |      i = i + 1
          |      val y = if i == 3 { break } else { i }
          |      s = s + y
          |    }
          |    return s * 10 + f(0)""".stripMargin)
      assert(Shell.Success(31) == result)
    }

    it("continue when the lambda is created inside the loop body") {
      val result = run(
        """    var s: Int = 0
          |    foreach i in 1..4 {
          |      val g: Int -> Int = (a) -> a * 10
          |      val y = if i == 2 { continue } else { g(i) }
          |      s = s + y
          |    }
          |    return s""".stripMargin)
      assert(Shell.Success(80) == result)
    }

    it("continue assigned to an existing var") {
      val result = run(
        """    val f: Int -> Int = (a) -> a + 1
          |    var s: Int = 0
          |    var y: Int = 0
          |    var i: Int = 0
          |    while i < 4 {
          |      i = i + 1
          |      y = if i == 2 { continue } else { f(i) }
          |      s = s + y
          |    }
          |    return s""".stripMargin)
      assert(Shell.Success(2 + 4 + 5) == result)
    }
  }

  describe("a top-level script (#2913)") {
    it("prints the value after skipping with continue") {
      val out = new java.io.ByteArrayOutputStream()
      val result = Console.withOut(new java.io.PrintStream(out, true)) {
        val saved = System.out
        System.setOut(new java.io.PrintStream(out, true))
        try
          shell.run(
            """val f: Int -> Int = (a) -> a + 1
              |var i: Int = 0
              |while i < 2 {
              |  i = i + 1
              |  val y = if i == 1 { continue } else { i }
              |  println(y)
              |}
              |""".stripMargin,
            "None",
            Array()
          )
        finally System.setOut(saved)
      }
      assert(!result.isInstanceOf[Shell.Failure])
      assert(out.toString.trim == "2")
    }
  }
}
