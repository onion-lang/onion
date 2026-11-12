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
    it("parses an ISO timestamp with Z or an offset to the exact instant (#2833)") {
      val r = shell.run(
        """def main(args: String[]): String {
          |  val a = DateTime::parse("2026-11-11T10:00:00Z")
          |  val b = DateTime::parse("2026-11-11T19:00:00+09:00")
          |  val c = DateTime::parse("2026-11-11T10:00:00.123Z")
          |  return a + " " + (a == b) + " " + (c - a)
          |}""".stripMargin, "None", Array())
      assert(Shell.Success("1794391200000 true 123") == r)
    }
    it("names the accepted forms when a timestamp does not parse (#2833)") {
      val r = shell.run(
        """def main(args: String[]): String {
          |  try { DateTime::parse("11/11/2026"); return "no-throw" }
          |  catch e: Exception { return e.getMessage() }
          |}""".stripMargin, "None", Array())
      assert(r.isInstanceOf[Shell.Success] && r.asInstanceOf[Shell.Success].value.toString.contains("expected 2026-11-11"))
    }
  }
}
