package onion.compiler.tools

import onion.tools.Shell

/**
 * A `try`/`catch`/`finally` statement's own `finally` block must run even when the
 * *catch* block itself completes abruptly by throwing (JLS 14.20.2: the finally
 * block executes whenever the try statement completes, however it completes,
 * including a catch clause exiting via an exception). This is the same "any exit
 * path is a candidate for finally" principle already fixed for return/break/continue
 * (`FinallyOnReturnSpec`) and for resource close() failures (0.75.0/0.76.0), applied
 * to an exception raised from within the catch clause itself.
 */
class CatchThrowsFinallySpec extends AbstractShellSpec {
  private val log = "class Log { public: static var s: String = \"\" }\n"

  it("runs finally when the catch block itself throws") {
    assert(Shell.Success("catch|fin|outer") == shell.run(
      log +
        "def f(): void { try { throw new RuntimeException(\"x\") } catch e: RuntimeException { Log::s = Log::s + \"catch\"\n throw new IllegalStateException(\"y\") } finally { Log::s = Log::s + \"|fin\" } }\n" +
        "def main(args: String[]): String { try { f() } catch e: IllegalStateException { Log::s = Log::s + \"|outer\" }\n return Log::s }",
      "None", Array()))
  }

  it("runs finally when a later catch (multi-catch) throws") {
    assert(Shell.Success("catch2|fin|outer") == shell.run(
      log +
        "def f(): void { try { throw new IllegalArgumentException(\"x\") } catch e: IllegalArgumentException { Log::s = Log::s + \"catch2\"\n throw new IllegalStateException(\"y\") } catch e: RuntimeException { Log::s = Log::s + \"catch1\"\n return } finally { Log::s = Log::s + \"|fin\" } }\n" +
        "def main(args: String[]): String { try { f() } catch e: IllegalStateException { Log::s = Log::s + \"|outer\" }\n return Log::s }",
      "None", Array()))
  }

  it("still runs finally when the try body's resource close and catch both succeed normally") {
    // Regression guard: the new catch-body exception guard must not interfere with
    // the already-correct normal-completion path for a resourced try/catch/finally.
    assert(Shell.Success("try|fin") == shell.run(
      "import { java.lang.AutoCloseable; }\n" +
        "class R conforms AutoCloseable { public: def this { }\n def close(): void { } }\n" +
        log +
        "def main(args: String[]): String { try (val r = new R()) { Log::s = Log::s + \"try\" } catch e: Exception { Log::s = Log::s + \"catch\" } finally { Log::s = Log::s + \"|fin\" }\n return Log::s }",
      "None", Array()))
  }
}
