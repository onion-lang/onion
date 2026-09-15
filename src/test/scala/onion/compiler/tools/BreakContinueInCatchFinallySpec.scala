package onion.compiler.tools

import onion.tools.Shell

/**
 * `break`/`continue` inside a `catch` block share the same `finallyStack` unwind
 * machinery (`emitBreak`/`emitContinue` in `ControlFlowEmitter`) already exercised for
 * a `catch` that `return`s (`FinallyOnReturnSpec`) or throws (`CatchThrowsFinallySpec`).
 * This locks in that a `break`/`continue` taken from inside a catch block still runs
 * that try statement's own `finally` -- exactly once -- before unwinding to the loop,
 * both with and without a resourced try (JLS 14.20.2 / 14.20.3.1).
 */
class BreakContinueInCatchFinallySpec extends AbstractShellSpec {
  private val log = "class Log { public: static var s: String = \"\" }\n"

  it("runs finally once when a break inside a catch exits the loop") {
    assert(Shell.Success("catch|fin") == shell.run(
      log +
        "def f(): void { while true { try { throw new RuntimeException(\"x\") } catch e: RuntimeException { Log::s = Log::s + \"catch\"\n break } finally { Log::s = Log::s + \"|fin\" } } }\n" +
        "def main(args: String[]): String { f()\n return Log::s }",
      "None", Array()))
  }

  it("runs finally each iteration when a continue inside a catch restarts the loop") {
    assert(Shell.Success("catch1|fin1catch2|fin2") == shell.run(
      log +
        "def f(): void { var i = 0\n while i < 2 { i = i + 1\n try { throw new RuntimeException(\"x\") } catch e: RuntimeException { Log::s = Log::s + \"catch\" + i\n continue } finally { Log::s = Log::s + \"|fin\" + i } } }\n" +
        "def main(args: String[]): String { f()\n return Log::s }",
      "None", Array()))
  }

  it("closes the resource before running finally when a break inside a catch exits a resourced try") {
    assert(Shell.Success("close|catch|fin") == shell.run(
      "import { java.lang.AutoCloseable; }\n" +
        "class R conforms AutoCloseable { public: def this { }\n def close(): void { Log::s = Log::s + \"close\" } }\n" +
        log +
        "def f(): void { while true { try (val r = new R()) { throw new RuntimeException(\"x\") } catch e: RuntimeException { Log::s = Log::s + \"|catch\"\n break } finally { Log::s = Log::s + \"|fin\" } } }\n" +
        "def main(args: String[]): String { f()\n return Log::s }",
      "None", Array()))
  }
}
