package onion.compiler.tools

import onion.tools.Shell

/**
 * `"12".toInt()` is the first thing a Kotlin or Scala programmer writes to read a number,
 * and it was E0005 ("did you mean: trim"). Strings had only the null/fallback forms
 * (`toIntOrNull`, `toIntOr`), so a script that expects valid input had to reach for
 * `Integer::parseInt`. `toInt`/`toLong`/`toDouble` now parse by the same rules as their
 * `OrNull` siblings and throw `NumberFormatException` naming the input when it is not one.
 */
class StringsToNumberSpec extends AbstractShellSpec {
  private def eval(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |    $body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("String to number conversion") {
    it("toInt as an extension and as a static call") {
      assert(eval("""return "" + ("12".toInt() + Strings::toInt(" 30 "))""") == Shell.Success("42"))
    }
    it("toLong") {
      assert(eval("""return "" + "-9000000000".toLong()""") == Shell.Success("-9000000000"))
    }
    it("toDouble, decimal and scientific") {
      assert(eval("""return "" + ("1.5".toDouble() + "1e3".toDouble())""") == Shell.Success("1001.5"))
    }
    it("toInt throws NumberFormatException naming the input") {
      assert(eval(
        """try { return "" + "abc".toInt() } catch e: NumberFormatException { return e.getMessage() }"""
      ) == Shell.Success("not an integer: \"abc\""))
    }
    it("toDouble rejects Java literal forms like its OrNull sibling") {
      assert(eval(
        """try { return "" + "1.0d".toDouble() } catch e: NumberFormatException { return e.getMessage() }"""
      ) == Shell.Success("not a number: \"1.0d\""))
    }
    it("toLong rejects a fraction") {
      assert(eval(
        """try { return "" + "1.5".toLong() } catch e: NumberFormatException { return e.getMessage() }"""
      ) == Shell.Success("not an integer: \"1.5\""))
    }
  }
}
