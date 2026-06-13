package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression tests for auto-CLI leaking raw NumberFormatException on invalid
 * flag values.  After the fix, onion.Cli.parseInt/parseLong/etc. are called
 * instead of bare Java parseInt, producing a friendly "invalid value for ..."
 * message via System.exit(1).
 */
class CliErrorMessageSpec extends AbstractShellSpec {
  describe("auto-CLI type conversion error messages") {
    it("valid Int flag works") {
      val result = shell.run(
        """
          |def main(name: String, count: Int = 3): void {
          |  IO::println(name + " x" + count)
          |}
          |""".stripMargin,
        "CliErrorInt.on",
        Array("hello", "--count", "5")
      )
      // void main returns null via reflection; Success(null) means it ran without crashing
      assert(Shell.Success(null) == result)
    }

    it("valid Long flag works") {
      val result = shell.run(
        """
          |def main(name: String, size: Long = 100L): void {
          |  IO::println(name + " " + size)
          |}
          |""".stripMargin,
        "CliErrorLong.on",
        Array("file", "--size", "9999999999")
      )
      assert(Shell.Success(null) == result)
    }

    it("valid Double flag works") {
      val result = shell.run(
        """
          |def main(name: String, ratio: Double = 1.0): void {
          |  IO::println(name + " " + ratio)
          |}
          |""".stripMargin,
        "CliErrorDouble.on",
        Array("test", "--ratio", "3.14")
      )
      assert(Shell.Success(null) == result)
    }

    it("Cli.parseInt helper exists and works via direct Java call") {
      val result = shell.run(
        """
          |import { onion.Cli; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val n: Int = Cli::parseInt("count", "42")
          |    return n.toString()
          |  }
          |}
          |""".stripMargin,
        "CliParseIntDirect.on",
        Array()
      )
      assert(Shell.Success("42") == result)
    }
  }
}
