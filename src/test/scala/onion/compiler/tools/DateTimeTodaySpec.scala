package onion.compiler.tools

import onion.tools.Shell

/**
 * `DateTime::today()` was E0005 ("did you mean: day"), though every DateTime value is an
 * epoch-millis Long and "the start of today" is a staple of date scripts. It now returns
 * `startOfDay(now())`, in the same default time zone the other DateTime functions use.
 */
class DateTimeTodaySpec extends AbstractShellSpec {
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

  describe("DateTime::today") {
    it("is the start of the current day") {
      assert(eval("""val t = DateTime::today()
                    |val ok = t == DateTime::startOfDay(DateTime::now()) && DateTime::hour(t) == 0 && DateTime::minute(t) == 0
                    |return "" + ok""".stripMargin) == Shell.Success("true"))
    }
    it("formats as today's date") {
      assert(eval("""return "" + (DateTime::format(DateTime::today(), "yyyy-MM-dd") == DateTime::nowString("yyyy-MM-dd"))""") ==
        Shell.Success("true"))
    }
  }
}
