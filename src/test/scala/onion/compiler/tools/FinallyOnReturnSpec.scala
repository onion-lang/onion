package onion.compiler.tools

import onion.tools.Shell

/**
 * A `finally` block runs on every exit path of the try/catch it guards, including
 * `return`, `break`, and `continue` (these used to skip it, silently dropping
 * cleanup). Observed through a static field the blocks append to, returned by main.
 */
class FinallyOnReturnSpec extends AbstractShellSpec {
  private val log = "class Log { public: static var s: String = \"\" }\n"

  it("runs finally when the try returns") {
    assert(Shell.Success("try|fin") == shell.run(
      log + "def f(): void { try { Log::s = Log::s + \"try\"\n return } finally { Log::s = Log::s + \"|fin\" } }\ndef main(args: String[]): String { f()\n return Log::s }", "None", Array()))
  }
  it("runs finally when a catch returns") {
    assert(Shell.Success("catch|fin") == shell.run(
      log + "def f(): void { try { throw new RuntimeException(\"x\") } catch e: Exception { Log::s = Log::s + \"catch\"\n return } finally { Log::s = Log::s + \"|fin\" } }\ndef main(args: String[]): String { f()\n return Log::s }", "None", Array()))
  }
  it("runs finally when a break exits the try") {
    assert(Shell.Success("try|fin|after") == shell.run(
      log + "def main(args: String[]): String { while true { try { Log::s = Log::s + \"try\"\n break } finally { Log::s = Log::s + \"|fin\" } }\n Log::s = Log::s + \"|after\"\n return Log::s }", "None", Array()))
  }
  it("runs finally each iteration when continue restarts the loop") {
    assert(Shell.Success("b1f1f2b3f3") == shell.run(
      log + "def main(args: String[]): String { var i = 0\n while i < 3 { i = i + 1\n try { if i == 2 { continue }\n Log::s = Log::s + \"b\" + i } finally { Log::s = Log::s + \"f\" + i } }\n return Log::s }", "None", Array()))
  }
  it("still runs finally on normal completion") {
    assert(Shell.Success("try|fin") == shell.run(
      log + "def main(args: String[]): String { try { Log::s = Log::s + \"try\" } finally { Log::s = Log::s + \"|fin\" }\n return Log::s }", "None", Array()))
  }
  it("lets a finally return override the try return") {
    assert(Shell.Success(2) == shell.run(
      "def f(): Int { try { return 1 } finally { return 2 } }\ndef main(args: String[]): Int { return f() }", "None", Array()))
  }
}
