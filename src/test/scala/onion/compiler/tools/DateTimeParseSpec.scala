package onion.compiler.tools

import onion.tools.Shell

/**
 * DateTime::parse supports date-only patterns (midnight, local zone) and fails
 * loudly on unparseable input instead of silently returning epoch 0.
 */
class DateTimeParseSpec extends AbstractShellSpec {
  describe("DateTime::parse") {
    it("parses a date-only pattern to midnight") {
      val r = shell.run(
        """def main(args: String[]): String {
          |  return DateTime::format(DateTime::parse("2026-07-02", "yyyy-MM-dd"))
          |}""".stripMargin, "None", Array())
      assert(Shell.Success("2026-07-02T00:00:00") == r)
    }
    it("throws on unparseable input instead of returning 0") {
      val r = shell.run(
        """def main(args: String[]): String {
          |  try { DateTime::parse("garbage", "yyyy-MM-dd"); return "no-throw" }
          |  catch e: Exception { return "threw" }
          |}""".stripMargin, "None", Array())
      assert(Shell.Success("threw") == r)
    }
  }
}
