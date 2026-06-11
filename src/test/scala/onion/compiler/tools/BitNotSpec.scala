package onion.compiler.tools

import onion.tools.Shell

/** Bitwise complement ~x for integral operands. */
class BitNotSpec extends AbstractShellSpec {

  describe("Bitwise complement") {
    it("complements int, long and promoted byte operands") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b = 5B
          |    return "" + ~5 + " " + ~0L + " " + ~b + " " + ~~7
          |  }
          |}
          |""".stripMargin,
        "BitNot.on",
        Array()
      )
      assert(Shell.Success("-6 -1 -6 7") == result)
    }

    it("rejects non-integral operands") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + ~1.5
          |  }
          |}
          |""".stripMargin,
        "BitNotDouble.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
