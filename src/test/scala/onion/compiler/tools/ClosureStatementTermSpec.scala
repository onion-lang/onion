package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression tests for ICE in CapturedVariableCollector when a closure body
 * contains a StatementTerm (e.g., a block with a val binding followed by an
 * if-expression).  Previously crashed with MatchError on StatementTerm.
 */
class ClosureStatementTermSpec extends AbstractShellSpec {
  describe("closure with StatementTerm in body") {
    it("groupBy with val binding and if-expression in closure does not crash") {
      val result = shell.run(
        """
          |import { java.util.*; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val list: List[String] = ["hello", "world", "hi", "there"]
          |    val grouped: Map[String, List[String]] = list.groupBy { s =>
          |      val x = s
          |      if x != null { x } else { "??" }
          |    }
          |    return grouped.size.toString()
          |  }
          |}
          |""".stripMargin,
        "ClosureStatementTerm.on",
        Array()
      )
      assert(Shell.Success("4") == result)
    }

    it("captures outer variable from closure with local val binding") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var prefix = "pre"
          |    val f = (s: String) -> {
          |      val x = s
          |      if x != null { prefix + "_" + x } else { prefix + "_null" }
          |    }
          |    return f("hello")
          |  }
          |}
          |""".stripMargin,
        "ClosureStatementTermCapture.on",
        Array()
      )
      assert(Shell.Success("pre_hello") == result)
    }
  }
}
