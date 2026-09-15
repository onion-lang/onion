package onion.compiler.tools

import onion.tools.Shell

/**
 * A `finally` block that itself throws while being run to unwind a `return` must run
 * exactly once, and its exception must propagate past this try statement's own
 * `catch`/`finally` machinery rather than being caught by a sibling `catch` clause of
 * the same try (JLS 14.20.2: abrupt completion of a `finally` block is a completion of
 * the whole try statement, not subject to that try's own catches) or re-executed a
 * second time. `runFinalliesDownTo` runs the finally inline while still emitting the
 * try body, i.e. inside the protected [tryStart, tryEnd) region, so without care its
 * own throw is caught by that same try's handlers.
 */
class FinallyThrowsOnReturnSpec extends AbstractShellSpec {
  private val log = "class Log { public: static var s: String = \"\" }\n"

  it("runs a throwing finally exactly once on return, uncaught by the try's own catch") {
    assert(Shell.Success("fin|outer") == shell.run(
      log +
        "def f(): void { try { return } finally { Log::s = Log::s + \"fin\"\n throw new RuntimeException(\"boom\") } }\n" +
        "def main(args: String[]): String { try { f() } catch e: RuntimeException { Log::s = Log::s + \"|outer\" }\n return Log::s }",
      "None", Array()))
  }

  it("does not misroute a throwing finally into a sibling catch of the same try") {
    assert(Shell.Success("fin|outer") == shell.run(
      log +
        "def f(): void { try { return } catch e: RuntimeException { Log::s = Log::s + \"caught\" } finally { Log::s = Log::s + \"fin\"\n throw new RuntimeException(\"boom\") } }\n" +
        "def main(args: String[]): String { try { f() } catch e: RuntimeException { Log::s = Log::s + \"|outer\" }\n return Log::s }",
      "None", Array()))
  }
}
