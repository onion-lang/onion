package onion.compiler.tools

import onion.tools.Shell

/**
 * Labeled break/continue for nested loops, plus the for-loop continue
 * semantics (continue must run the update step, not skip it).
 */
class LabeledLoopSpec extends AbstractShellSpec {

  describe("Labeled loops") {
    it("break label exits the labeled outer loop") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var found = ""
          |    outer: foreach i: Int in 0..3 {
          |      foreach j: Int in 0..3 {
          |        if i * j == 6 { found = "" + i + j; break outer }
          |      }
          |    }
          |    return found
          |  }
          |}
          |""".stripMargin,
        "BreakLabel.on",
        Array()
      )
      assert(Shell.Success("23") == result)
    }

    it("continue label resumes the labeled outer loop") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var log = ""
          |    o2: for var i = 0; i < 3; i += 1 {
          |      foreach j: Int in 0..5 {
          |        if j > i { continue o2 }
          |        log = log + i + "" + j + ";"
          |      }
          |    }
          |    return log
          |  }
          |}
          |""".stripMargin,
        "ContinueLabel.on",
        Array()
      )
      assert(Shell.Success("00;10;11;20;21;22;") == result)
    }

    it("reports E0058 for an undefined label") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    foreach i: Int in 0..3 {
          |      break nosuch
          |    }
          |    return "no"
          |  }
          |}
          |""".stripMargin,
        "BadLabel.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }

  describe("For-loop continue") {
    it("runs the update step (does not skip the increment)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var log = ""
          |    for var i = 0; i < 3; i += 1 {
          |      if i == 1 { continue }
          |      log = log + i + ";"
          |    }
          |    return log
          |  }
          |}
          |""".stripMargin,
        "ForContinue.on",
        Array()
      )
      assert(Shell.Success("0;2;") == result)
    }
  }
}
