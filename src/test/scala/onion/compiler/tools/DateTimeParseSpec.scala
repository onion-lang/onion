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
    it("honours an offset in the text of a patterned parse (#3014)") {
      val r = shell.run(
        """def main(args: String[]): String {
          |  val p = "yyyy-MM-dd'T'HH:mmXXX"
          |  val a = DateTime::parse("2026-11-11T09:30Z", p)
          |  val b = DateTime::parse("2026-11-11T18:30+09:00", p)
          |  return (a == DateTime::parse("2026-11-11T09:30:00Z")) + " " + (a == b)
          |}""".stripMargin, "None", Array())
      assert(Shell.Success("true true") == r)
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
    it("rejects a clock-hour pattern without an am/pm field instead of returning midnight (#3145)") {
      val r = shell.run(
        """def main(args: String[]): String {
          |  try { DateTime::parse("2026-11-11T09:30Z", "yyyy-MM-dd'T'hh:mmXXX"); return "no-throw" }
          |  catch e: Exception { return e.getMessage() }
          |}""".stripMargin, "None", Array())
      assert(r.isInstanceOf[Shell.Success] && r.asInstanceOf[Shell.Success].value.toString.contains("am/pm"))
    }
    it("parses a clock-hour pattern that has an am/pm field (#3145)") {
      val r = shell.run(
        """def main(args: String[]): String {
          |  val a = DateTime::parse("2026-11-11T09:30 PM Z", "yyyy-MM-dd'T'hh:mm a X")
          |  return (a == DateTime::parse("2026-11-11T21:30:00Z")) + ""
          |}""".stripMargin, "None", Array())
      assert(Shell.Success("true") == r)
    }
    it("honours an offset in a date-only patterned parse (#3059)") {
      val r = shell.run(
        """def main(args: String[]): String {
          |  val z = DateTime::parse("2026-11-11Z", "yyyy-MM-ddXXX")
          |  val t = DateTime::parse("2026-11-11+09:00", "yyyy-MM-ddXXX")
          |  return z + " " + (z - t)
          |}""".stripMargin, "None", Array())
      assert(Shell.Success("1794355200000 32400000") == r)
    }
  }
}
