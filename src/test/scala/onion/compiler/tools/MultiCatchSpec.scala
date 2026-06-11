package onion.compiler.tools

import onion.tools.Shell

/** Multi-catch: catch e: A | B handles both exception types in one body. */
class MultiCatchSpec extends AbstractShellSpec {

  describe("Multi-catch") {
    it("handles each listed exception type with the shared body") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def f(mode: Int): String {
          |    try {
          |      if mode == 0 { throw new IllegalStateException("ise") }
          |      if mode == 1 { throw new IllegalArgumentException("iae") }
          |      return "none"
          |    } catch e: IllegalArgumentException | IllegalStateException {
          |      return "caught:" + e.getMessage()
          |    }
          |  }
          |  static def main(args: String[]): String {
          |    return Test::f(0) + "/" + Test::f(1) + "/" + Test::f(2)
          |  }
          |}
          |""".stripMargin,
        "MultiCatch.on",
        Array()
      )
      assert(Shell.Success("caught:ise/caught:iae/none") == result)
    }

    it("combines with following catch clauses and finally") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var log = ""
          |    try {
          |      throw new RuntimeException("re")
          |    } catch e: IllegalArgumentException | IllegalStateException {
          |      log = log + "multi"
          |    } catch e: Exception {
          |      log = log + "general"
          |    } finally {
          |      log = log + "+fin"
          |    }
          |    return log
          |  }
          |}
          |""".stripMargin,
        "MultiCatchFallthrough.on",
        Array()
      )
      assert(Shell.Success("general+fin") == result)
    }
  }
}
