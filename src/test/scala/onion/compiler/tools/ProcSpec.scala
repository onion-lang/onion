package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for the onion.Proc scripting stdlib (issue #123).
 * Uses POSIX tools (echo, sh); CI runs on Linux.
 */
class ProcSpec extends AbstractShellSpec {

  describe("Proc") {
    it("runs a command and captures stdout") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return Proc::run("echo", "hello onion")
          |  }
          |}
          |""".stripMargin,
        "ProcRun.on",
        Array()
      )
      assert(Shell.Success("hello onion") == result)
    }

    it("captures status, stdout and stderr without throwing") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r = Proc::capture("sh", "-c", "echo out; echo err 1>&2; exit 3")
          |    return r.status() + ":" + r.stdout().strip() + ":" + r.stderr().strip() + ":" + r.failed()
          |  }
          |}
          |""".stripMargin,
        "ProcCapture.on",
        Array()
      )
      assert(Shell.Success("3:out:err:true") == result)
    }

    it("throws a descriptive error when run fails") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    try {
          |      Proc::run("sh", "-c", "echo broken 1>&2; exit 9")
          |      return "no error"
          |    } catch e: Exception {
          |      val m = e.getMessage()
          |      if m.contains("9") && m.contains("broken") { return "reported" } else { return m }
          |    }
          |  }
          |}
          |""".stripMargin,
        "ProcRunFails.on",
        Array()
      )
      assert(Shell.Success("reported") == result)
    }

    it("returns the exit status from exec") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return Proc::exec("sh", "-c", "exit 7")
          |  }
          |}
          |""".stripMargin,
        "ProcExec.on",
        Array()
      )
      assert(Shell.Success(7) == result)
    }

    it("runs in a given working directory") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return Proc::runIn("/tmp", "sh", "-c", "pwd")
          |  }
          |}
          |""".stripMargin,
        "ProcRunIn.on",
        Array()
      )
      assert(Shell.Success("/tmp") == result)
    }
  }
}
