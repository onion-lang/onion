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

    it("accepts both lowercase and uppercase float/double suffixes (f/F/d/D)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: Float  = 2.5f
          |    val b: Float  = 2.5F
          |    val c: Double = 2.5d
          |    val e: Double = 2.5D
          |    val g: Float  = 3f
          |    return "" + a + " " + b + " " + c + " " + e + " " + g
          |  }
          |}
          |""".stripMargin,
        "FloatSuffixes.on",
        Array()
      )
      assert(Shell.Success("2.5 2.5 2.5 2.5 3.0") == result)
    }
  }
}
