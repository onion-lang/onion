package onion.compiler.tools

import onion.tools.Shell

class BreakContinueSpec extends AbstractShellSpec {
  describe("Break and Continue statements") {
    it("break exits loop early") {
      val result = shell.run(
        """
          |class BreakTest {
          |public:
          |  static def main(args: String[]): Int {
          |    count = 0
          |    i = 0
          |    while i < 10 {
          |      if i == 5 {
          |        break
          |      }
          |      count = count + 1
          |      i = i + 1
          |    }
          |    return count
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(5) == result)
    }
    
    it("continue skips to next iteration") {
      val result = shell.run(
        """
          |class ContinueTest {
          |public:
          |  static def main(args: String[]): Int {
          |    count = 0
          |    i = 0
          |    while i < 10 {
          |      i = i + 1
          |      if i % 2 == 0 {
          |        continue
          |      }
          |      count = count + 1
          |    }
          |    return count
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(5) == result) // Count odd numbers: 1, 3, 5, 7, 9
    }
  }
}