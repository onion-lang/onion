package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for syntax ergonomics: else-if chains and single-line blocks
 * (statements terminated by the closing brace instead of ';'/newline).
 */
class SyntaxErgonomicsSpec extends AbstractShellSpec {

  describe("else if") {
    it("chains else if as statements") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def grade(score: Int): String {
          |    if score >= 90 {
          |      return "A"
          |    } else if score >= 70 {
          |      return "B"
          |    } else if score >= 50 {
          |      return "C"
          |    } else {
          |      return "D"
          |    }
          |  }
          |  static def main(args: String[]): String {
          |    return grade(73) + grade(95) + grade(51) + grade(10)
          |  }
          |}
          |""".stripMargin,
        "ElseIfChain.on",
        Array()
      )
      assert(Shell.Success("BACD") == result)
    }

    it("uses else if chains as expressions") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val score = 73
          |    val grade = if score >= 90 { "A" } else if score >= 70 { "B" } else { "C" }
          |    return grade
          |  }
          |}
          |""".stripMargin,
        "ElseIfExpression.on",
        Array()
      )
      assert(Shell.Success("B") == result)
    }

    it("works without a trailing else") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x = 0
          |    val n = 5
          |    if n > 10 {
          |      x = 1
          |    } else if n > 3 {
          |      x = 2
          |    }
          |    return x
          |  }
          |}
          |""".stripMargin,
        "ElseIfNoElse.on",
        Array()
      )
      assert(Shell.Success(2) == result)
    }
  }

  describe("single-line blocks") {
    it("allows return before the closing brace") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def f(n: Int): Int {
          |    if n == 0 { return 0 }
          |    return n * 2
          |  }
          |  static def main(args: String[]): Int {
          |    return f(0) + f(3)
          |  }
          |}
          |""".stripMargin,
        "SingleLineReturn.on",
        Array()
      )
      assert(Shell.Success(6) == result)
    }

    it("allows break and continue before the closing brace") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var sum = 0
          |    var i = 0
          |    while true {
          |      i = i + 1
          |      if i > 5 { break }
          |      if i == 2 { continue }
          |      sum = sum + i
          |    }
          |    return sum
          |  }
          |}
          |""".stripMargin,
        "SingleLineBreakContinue.on",
        Array()
      )
      assert(Shell.Success(13) == result)
    }

    it("allows val declarations before the closing brace") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x = if true { val y = 21
          |      y * 2 } else { 0 }
          |    return x
          |  }
          |}
          |""".stripMargin,
        "SingleLineVal.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("allows throw before the closing brace") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    try {
          |      if true { throw new RuntimeException("boom") }
          |      return "no"
          |    } catch e: Exception {
          |      return e.getMessage()
          |    }
          |  }
          |}
          |""".stripMargin,
        "SingleLineThrow.on",
        Array()
      )
      assert(Shell.Success("boom") == result)
    }

    it("still treats a same-line if after return as the return value") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def f(n: Int): String {
          |    return if n > 0 { "pos" } else { "neg" }
          |  }
          |  static def main(args: String[]): String {
          |    return f(1) + f(-1)
          |  }
          |}
          |""".stripMargin,
        "ReturnIfValue.on",
        Array()
      )
      assert(Shell.Success("posneg") == result)
    }
  }
}
