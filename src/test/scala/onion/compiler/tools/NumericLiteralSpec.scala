package onion.compiler.tools

import onion.tools.Shell

/** Numeric literal ergonomics: underscore separators and binary literals. */
class NumericLiteralSpec extends AbstractShellSpec {

  describe("Numeric literals") {
    it("accepts underscore separators in int, long, hex and double literals") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val big = 1_000_000
          |    val hex = 0xFF_FF
          |    val lng = 1_000_000_000_000L
          |    val dbl = 1_234.5
          |    return "" + big + " " + hex + " " + lng + " " + dbl
          |  }
          |}
          |""".stripMargin,
        "Underscores.on",
        Array()
      )
      assert(Shell.Success("1000000 65535 1000000000000 1234.5") == result)
    }

    it("accepts binary literals with suffixes") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b = 0b1010_1010
          |    val l = 0b1111L
          |    return "" + b + " " + l
          |  }
          |}
          |""".stripMargin,
        "BinaryLiterals.on",
        Array()
      )
      assert(Shell.Success("170 15") == result)
    }
  }
}
