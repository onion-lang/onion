package onion.compiler.tools

import onion.tools.Shell

/** do { body } while cond — body-first loop with break/continue support. */
class DoWhileSpec extends AbstractShellSpec {

  describe("do-while") {
    it("loops until the condition fails") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var i = 0
          |    do {
          |      i = i + 1
          |    } while i < 3
          |    return "" + i
          |  }
          |}
          |""".stripMargin,
        "DoWhileBasic.on",
        Array()
      )
      assert(Shell.Success("3") == result)
    }

    it("executes the body at least once") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var j = 10
          |    do { j = j + 1 } while j < 5
          |    return "" + j
          |  }
          |}
          |""".stripMargin,
        "DoWhileOnce.on",
        Array()
      )
      assert(Shell.Success("11") == result)
    }

    it("supports break and continue (continue jumps to the check)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var k = 0
          |    var sum = 0
          |    do {
          |      k = k + 1
          |      if k == 2 { continue }
          |      if k >= 5 { break }
          |      sum = sum + k
          |    } while true
          |    return "" + sum
          |  }
          |}
          |""".stripMargin,
        "DoWhileBreakContinue.on",
        Array()
      )
      assert(Shell.Success("8") == result)
    }

    it("does not conflict with do-notation") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r = do[Option] { a <- Option::some(1); ret a + 1 }
          |    return "" + r.get()
          |  }
          |}
          |""".stripMargin,
        "DoNotationStillWorks.on",
        Array()
      )
      assert(Shell.Success("2") == result)
    }
  }
}
