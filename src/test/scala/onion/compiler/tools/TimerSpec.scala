package onion.compiler.tools

import onion.tools.Shell

class TimerSpec extends AbstractShellSpec {
  describe("Timing module") {
    describe("nanos/elapsedNanos") {
      it("measures elapsed time in nanoseconds") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val start = Timing::nanos();
            |    var sum: Int = 0;
            |    for var i: Int = 0; i < 1000; i = i + 1 {
            |      sum = sum + i;
            |    }
            |    val elapsed = Timing::elapsedNanos(start);
            |    return "" + (elapsed > 0L);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }
    }

    describe("millis/elapsedMillis") {
      it("measures elapsed time in milliseconds") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val start = Timing::millis();
            |    Timing::sleep(10L);
            |    val elapsed = Timing::elapsedMillis(start);
            |    return "" + (elapsed >= 5L);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }
    }

    describe("elapsedMs") {
      it("returns elapsed time as double milliseconds") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val start = Timing::nanos();
            |    Timing::sleep(10L);
            |    val elapsed: Double = Timing::elapsedMs(start);
            |    return "" + (elapsed > 5.0);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }
    }

    describe("formatNanos") {
      it("formats nanoseconds") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Timing::formatNanos(500L);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("500ns") == result)
      }

      it("formats microseconds") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val s = Timing::formatNanos(1500L);
            |    return "" + s.endsWith("us");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }

      it("formats milliseconds") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val s = Timing::formatNanos(1500000L);
            |    return "" + s.endsWith("ms");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }

      it("formats seconds") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val s = Timing::formatNanos(1500000000L);
            |    return "" + s.endsWith("s");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }
    }

    describe("formatMillis") {
      it("formats milliseconds") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Timing::formatMillis(500L);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("500ms") == result)
      }

      it("formats seconds") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val s = Timing::formatMillis(1500L);
            |    return "" + s.endsWith("s");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }

      it("formats minutes and seconds") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Timing::formatMillis(90000L);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("1m30s") == result)
      }
    }

    describe("sleep") {
      it("sleeps for specified milliseconds") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val start = Timing::millis();
            |    Timing::sleep(20L);
            |    val elapsed = Timing::elapsedMillis(start);
            |    return "" + (elapsed >= 15L);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }
    }

    describe("measure with function") {
      it("measures function execution and returns result") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val result = Timing::measure(() -> {
            |      Timing::sleep(5L);
            |      return "done";
            |    });
            |    return result;
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("done") == result)
      }

      it("measures function with label") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val result = Timing::measure("task", () -> {
            |      return 42;
            |    });
            |    return "" + result;
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("42") == result)
      }

      it("time returns elapsed nanoseconds") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val elapsed = Timing::time(() -> {
            |      Timing::sleep(5L);
            |      return "x";
            |    });
            |    return "" + (elapsed > 0L);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }
    }
  }
}
