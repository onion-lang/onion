package onion.compiler.tools

import onion.tools.Shell

/**
 * `FinallyThrowsOnReturnSpec` locks in that a `finally` block which itself throws while
 * being run to unwind an early exit propagates past its own try statement's `catch`
 * (JLS 14.20.2) exactly once, for a `return`. `break`/`continue` unwind through the exact
 * same `runFinalliesDownTo` mechanism (see `ControlFlowEmitter`), so the same guarantee
 * must hold for them too -- this locks that in explicitly, plus the try-with-resources
 * shape (JLS 14.20.3.1: resources close, in the protected region, before the finally's own
 * hole-excluded throw).
 */
class FinallyThrowsOnBreakContinueSpec extends AbstractShellSpec {
  private val log = "class Log { public: static var s: String = \"\" }\n"

  it("runs a throwing finally exactly once on break, uncaught by the try's own sibling catch") {
    assert(Shell.Success("fin|outer") == shell.run(
      log +
        "def f(): void { while true { try { break } catch e: RuntimeException { Log::s = Log::s + \"caught\" } finally { Log::s = Log::s + \"fin\"\n throw new RuntimeException(\"boom\") } } }\n" +
        "def main(args: String[]): String { try { f() } catch e: RuntimeException { Log::s = Log::s + \"|outer\" }\n return Log::s }",
      "None", Array()))
  }

  it("runs a throwing finally exactly once on continue, uncaught by the try's own sibling catch") {
    assert(Shell.Success("fin|outer") == shell.run(
      log +
        "def f(): void { var i = 0\n while i < 1 { i = i + 1\n try { continue } catch e: RuntimeException { Log::s = Log::s + \"caught\" } finally { Log::s = Log::s + \"fin\"\n throw new RuntimeException(\"boom\") } } }\n" +
        "def main(args: String[]): String { try { f() } catch e: RuntimeException { Log::s = Log::s + \"|outer\" }\n return Log::s }",
      "None", Array()))
  }

  it("runs a throwing finally exactly once on return from a resourced try, after closing the resource") {
    assert(Shell.Success("closefin|outer") == shell.run(
      "import { java.lang.AutoCloseable; }\n" +
        "class R conforms AutoCloseable { public: def this { }\n def close(): void { Log::s = Log::s + \"close\" } }\n" +
        log +
        "def f(): void { try (val r = new R()) { return } catch e: RuntimeException { Log::s = Log::s + \"caught\" } finally { Log::s = Log::s + \"fin\"\n throw new RuntimeException(\"boom\") } }\n" +
        "def main(args: String[]): String { try { f() } catch e: RuntimeException { Log::s = Log::s + \"|outer\" }\n return Log::s }",
      "None", Array()))
  }
}
