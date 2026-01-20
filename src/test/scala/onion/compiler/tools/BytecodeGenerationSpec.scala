package onion.compiler.tools

import onion.tools.Shell

class BytecodeGenerationSpec extends AbstractShellSpec {
  describe("Bytecode generation helpers") {
    it("handles loops with local context tracking") {
      val result = shell.run(
        """
          |class BytecodeHelperSample {
          |public:
          |  static def sum(args: String[]): Int {
          |    var total = 0
          |    var i = 0
          |    while (i < 4) {
          |      total += i
          |      i += 1
          |    }
          |    return total
          |  }
          |
          |  static def main(args: String[]): Int {
          |    return BytecodeHelperSample::sum(args)
          |  }
          |}
          |""".stripMargin,
        "BytecodeHelperSample.on",
        Array()
      )
      assert(Shell.Success(6) == result)
    }
  }
}
