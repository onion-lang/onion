package onion.compiler.tools

import onion.tools.Shell

/**
 * Execution coverage for onion.DateTime members that DateTimeSpec,
 * DateTimeParseSpec, and DateTimeExtraSpec never exercised: addHours,
 * addMinutes, addSeconds, diff, diffSeconds, dayOfYear, startOfDay,
 * endOfDay, and both nowString overloads. Uses fixed timestamps (via
 * DateTime::of) so the assertions are stable, following the same pattern
 * as DateTimeExtraSpec.
 */
class DateTimeCoverageSpec extends AbstractShellSpec {

  private def runBool(body: String, expect: Boolean): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Boolean {\n" + body + "\n  }\n}\n"
    assert(Shell.Success(expect) == shell.run(src, "DateTimeCoverageBool.on", Array()))
  }

  describe("DateTime arithmetic helpers") {
    it("addHours adds hours to a timestamp") {
      runBool(
        "val t = DateTime::of(2024, 3, 15, 10, 0, 0)\n" +
        "val expected = DateTime::of(2024, 3, 15, 14, 0, 0)\n" +
        "return DateTime::addHours(t, 4) == expected",
        true
      )
    }

    it("addMinutes adds minutes to a timestamp") {
      runBool(
        "val t = DateTime::of(2024, 3, 15, 10, 0, 0)\n" +
        "val expected = DateTime::of(2024, 3, 15, 11, 30, 0)\n" +
        "return DateTime::addMinutes(t, 90) == expected",
        true
      )
    }

    it("addSeconds adds seconds to a timestamp") {
      runBool(
        "val t = DateTime::of(2024, 3, 15, 10, 0, 0)\n" +
        "val expected = DateTime::of(2024, 3, 15, 10, 2, 5)\n" +
        "return DateTime::addSeconds(t, 125) == expected",
        true
      )
    }
  }

  describe("DateTime comparison helpers") {
    it("diff returns the millisecond difference between two timestamps") {
      runBool(
        "val t1 = DateTime::of(2024, 3, 15, 10, 0, 5)\n" +
        "val t2 = DateTime::of(2024, 3, 15, 10, 0, 0)\n" +
        "return DateTime::diff(t1, t2) == 5000L",
        true
      )
    }

    it("diffSeconds returns the whole-second difference between two timestamps") {
      runBool(
        "val t1 = DateTime::of(2024, 3, 15, 10, 1, 30)\n" +
        "val t2 = DateTime::of(2024, 3, 15, 10, 0, 0)\n" +
        "return DateTime::diffSeconds(t1, t2) == 90L",
        true
      )
    }
  }

  describe("DateTime component helper") {
    it("dayOfYear returns the day-of-year ordinal") {
      runBool(
        // 2024 is a leap year: Jan(31) + Feb(29) + 15 = 75
        "val t = DateTime::of(2024, 3, 15, 12, 0, 0)\n" +
        "return DateTime::dayOfYear(t) == 75",
        true
      )
    }
  }

  describe("DateTime day-boundary helpers") {
    it("startOfDay returns midnight for the given day") {
      runBool(
        "val t = DateTime::of(2024, 3, 15, 14, 37, 21)\n" +
        "val expected = DateTime::of(2024, 3, 15, 0, 0, 0)\n" +
        "return DateTime::startOfDay(t) == expected",
        true
      )
    }

    it("endOfDay returns 23:59:59 for the given day") {
      runBool(
        "val t = DateTime::of(2024, 3, 15, 14, 37, 21)\n" +
        "val end = DateTime::endOfDay(t)\n" +
        "return DateTime::hour(end) == 23 && DateTime::minute(end) == 59 && DateTime::second(end) == 59",
        true
      )
    }
  }

  describe("DateTime current-time string formatting") {
    it("nowString returns a non-empty ISO-formatted string") {
      runBool(
        "val s = DateTime::nowString()\n" +
        "return s.length > 0 && s.indexOf(\"T\") >= 0",
        true
      )
    }

    it("nowString(pattern) formats the current time with a custom pattern") {
      runBool(
        "val s = DateTime::nowString(\"yyyy\")\n" +
        "return s == (DateTime::year(DateTime::now())).toString()",
        true
      )
    }
  }
}
