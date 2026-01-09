package onion.compiler.tools

import onion.tools.Shell

class DateTimeSpec extends AbstractShellSpec {
  describe("DateTime library") {
    describe("current time") {
      it("returns current epoch milliseconds") {
        val result = shell.run(
          """
            |import { onion.DateTime; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val now: Long = DateTime::now();
            |    if (now > 0L) {
            |      return "positive";
            |    } else {
            |      return "zero or negative";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("positive") == result)
      }
    }

    describe("factory methods") {
      it("creates date from year, month, day") {
        val result = shell.run(
          """
            |import { onion.DateTime; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val date: Long = DateTime::of(2024, 1, 15);
            |    val year: Int = DateTime::year(date);
            |    val month: Int = DateTime::month(date);
            |    val day: Int = DateTime::day(date);
            |    return year.toString() + "-" + month.toString() + "-" + day.toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("2024-1-15") == result)
      }

      it("creates datetime from year, month, day, hour, minute, second") {
        val result = shell.run(
          """
            |import { onion.DateTime; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val dt: Long = DateTime::of(2024, 6, 15, 10, 30, 45);
            |    val hour: Int = DateTime::hour(dt);
            |    val minute: Int = DateTime::minute(dt);
            |    val second: Int = DateTime::second(dt);
            |    return hour.toString() + ":" + minute.toString() + ":" + second.toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("10:30:45") == result)
      }
    }

    describe("components") {
      it("extracts year") {
        val result = shell.run(
          """
            |import { onion.DateTime; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val date: Long = DateTime::of(2025, 3, 20);
            |    return DateTime::year(date).toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("2025") == result)
      }

      it("extracts month (1-12)") {
        val result = shell.run(
          """
            |import { onion.DateTime; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val date: Long = DateTime::of(2025, 12, 1);
            |    return DateTime::month(date).toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("12") == result)
      }

      it("extracts day (1-31)") {
        val result = shell.run(
          """
            |import { onion.DateTime; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val date: Long = DateTime::of(2025, 1, 31);
            |    return DateTime::day(date).toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("31") == result)
      }

      it("extracts dayOfWeek (1=Monday, 7=Sunday)") {
        val result = shell.run(
          """
            |import { onion.DateTime; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    // 2024-01-01 is Monday
            |    val date: Long = DateTime::of(2024, 1, 1);
            |    return DateTime::dayOfWeek(date).toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("1") == result)
      }
    }

    describe("arithmetic") {
      it("adds days") {
        val result = shell.run(
          """
            |import { onion.DateTime; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val date: Long = DateTime::of(2024, 1, 1);
            |    val later: Long = DateTime::addDays(date, 10);
            |    return DateTime::day(later).toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("11") == result)
      }

      it("adds months") {
        val result = shell.run(
          """
            |import { onion.DateTime; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val date: Long = DateTime::of(2024, 1, 15);
            |    val later: Long = DateTime::addMonths(date, 2);
            |    return DateTime::month(later).toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("3") == result)
      }

      it("adds years") {
        val result = shell.run(
          """
            |import { onion.DateTime; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val date: Long = DateTime::of(2024, 6, 15);
            |    val later: Long = DateTime::addYears(date, 5);
            |    return DateTime::year(later).toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("2029") == result)
      }
    }

    describe("comparison") {
      it("compares isBefore") {
        val result = shell.run(
          """
            |import { onion.DateTime; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val d1: Long = DateTime::of(2024, 1, 1);
            |    val d2: Long = DateTime::of(2024, 12, 31);
            |    if (DateTime::isBefore(d1, d2)) {
            |      return "before";
            |    } else {
            |      return "not before";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("before") == result)
      }

      it("compares isAfter") {
        val result = shell.run(
          """
            |import { onion.DateTime; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val d1: Long = DateTime::of(2024, 12, 31);
            |    val d2: Long = DateTime::of(2024, 1, 1);
            |    if (DateTime::isAfter(d1, d2)) {
            |      return "after";
            |    } else {
            |      return "not after";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("after") == result)
      }

      it("calculates diffDays") {
        val result = shell.run(
          """
            |import { onion.DateTime; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val d1: Long = DateTime::of(2024, 1, 1);
            |    val d2: Long = DateTime::of(2024, 1, 11);
            |    return DateTime::diffDays(d2, d1).toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("10") == result)
      }
    }

    describe("formatting") {
      it("formats with pattern") {
        val result = shell.run(
          """
            |import { onion.DateTime; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val date: Long = DateTime::of(2024, 3, 15);
            |    return DateTime::format(date, "yyyy-MM-dd");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("2024-03-15") == result)
      }
    }
  }
}
