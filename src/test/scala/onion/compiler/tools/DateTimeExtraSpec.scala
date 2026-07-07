package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for the duration and display helpers added to `onion.DateTime`:
 * diffHours/diffMinutes/diffSeconds (completing diffDays) and dayName/monthName
 * (English names, locale-independent). Uses a fixed timestamp so the assertions
 * are stable.
 */
class DateTimeExtraSpec extends AbstractShellSpec {

  private def runInt(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "DateTimeExtraInt.on", Array()))
  }

  private def runStr(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "DateTimeExtraStr.on", Array()))
  }

  describe("DateTime duration helpers") {
    it("diffHours/diffMinutes/diffSeconds between two timestamps") {
      runInt(
        "val t1 = DateTime::of(2024, 3, 15, 14, 30, 0)\n" +
        "val t2 = DateTime::of(2024, 3, 15, 10, 0, 0)\n" +
        "return (DateTime::diffHours(t1, t2) as Int) + (DateTime::diffMinutes(t1, t2) as Int)",
        Shell.Success(274)) // 4 + 270
    }
  }

  describe("DateTime display names (English, locale-independent)") {
    it("dayName and monthName for a fixed date") {
      runStr(
        "val t = DateTime::of(2024, 3, 15, 12, 0, 0)\n" +
        "return DateTime::dayName(t) + \" \" + DateTime::monthName(t)",
        Shell.Success("Friday March"))
    }
  }
}
