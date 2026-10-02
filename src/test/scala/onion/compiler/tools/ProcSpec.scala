package onion.compiler.tools

import onion.tools.Shell

import java.io.File
import java.nio.file.{Files, Path}

/**
 * Tests for the onion.Proc scripting stdlib (issue #123).
 *
 * `Proc` starts a program directly (no shell), so the child commands are platform-specific:
 * POSIX `sh`/`echo` everywhere but Windows, `cmd.exe /c` on Windows, where neither `sh` nor an
 * `echo` executable is on the PATH. Each test runs the same scenario on both, with the POSIX
 * commands and expected values exactly as they were.
 */
class ProcSpec extends AbstractShellSpec {

  private val windows = File.separatorChar == '\\'

  /** An Onion string literal for `s`. */
  private def lit(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  /** Varargs for running `posix` under `sh -c`, or `windows` under `cmd /c` on Windows. */
  private def script(posix: String, windowsScript: String): String =
    if (windows) s""""cmd", "/c", ${lit(windowsScript)}""" else s""""sh", "-c", ${lit(posix)}"""

  /**
   * The working directory for the `*In` variants: `/tmp` on POSIX; on Windows a fresh
   * directory under the temp dir, by its real path so `cd` prints it in the same spelling
   * (the temp dir is often given in 8.3 short form).
   */
  private lazy val workDir: String =
    if (windows) Files.createTempDirectory("onion-proc").toRealPath().toString else "/tmp"

  describe("Proc") {
    it("runs a command and captures stdout") {
      // `echo` is an executable on POSIX, a cmd.exe builtin on Windows.
      val command =
        if (windows) "\"cmd\", \"/c\", \"echo hello onion\"" else "\"echo\", \"hello onion\""
      val result = shell.run(
        s"""
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return Proc::run($command)
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
        s"""
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r = Proc::capture(${script("echo out; echo err 1>&2; exit 3", "echo out& echo err 1>&2& exit 3")})
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
      // The message also quotes the command, which itself contains "9" and "broken"; check the
      // parts only a real run produces: the status and the child's stderr.
      val result = shell.run(
        s"""
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    try {
          |      Proc::run(${script("echo broken 1>&2; exit 9", "echo broken 1>&2& exit 9")})
          |      return "no error"
          |    } catch e: Exception {
          |      val m = e.getMessage()
          |      if m.contains("exited with 9") && m.endsWith(": broken") { return "reported" } else { return m }
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
        s"""
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return Proc::exec(${script("exit 7", "exit 7")})
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
        s"""
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return Proc::runIn(${lit(workDir)}, ${script("pwd", "cd")})
          |  }
          |}
          |""".stripMargin,
        "ProcRunIn.on",
        Array()
      )
      assert(Shell.Success(workDir) == result)
    }

    it("captures status, stdout and stderr from a given working directory") {
      val result = shell.run(
        s"""
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r = Proc::captureIn(${lit(workDir)}, ${script("pwd 1>&2; exit 5", "cd 1>&2& exit 5")})
          |    return r.status() + ":" + r.stderr().strip()
          |  }
          |}
          |""".stripMargin,
        "ProcCaptureIn.on",
        Array()
      )
      assert(Shell.Success(s"5:$workDir") == result)
    }

    it("returns the exit status from exec in a given working directory") {
      val posixCheck = "[ \"$(pwd)\" = \"" + workDir + "\" ]"
      val windowsCheck = "if /i \"%CD%\"==\"" + workDir + "\" (exit 0) else (exit 1)"
      val result = shell.run(
        s"""
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return Proc::execIn(${lit(workDir)}, ${script(posixCheck, windowsCheck)})
          |  }
          |}
          |""".stripMargin,
        "ProcExecIn.on",
        Array()
      )
      assert(Shell.Success(0) == result)
    }
  }
}
