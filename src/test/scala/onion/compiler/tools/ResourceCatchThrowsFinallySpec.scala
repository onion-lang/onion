package onion.compiler.tools

import onion.tools.Shell

/**
 * `try (val r = ...) { throws } catch e: T { throws } finally { ... }` combines three
 * exit paths this file's `ControlFlowEmitter` had separate prior fixes for (resource
 * close before catch: 0.75.0-0.76.0; a throwing catch body still running finally:
 * 0.77.0; see CHANGELOG.md), but no existing spec exercises all three together. This
 * guards that the resource is closed exactly once, before the catch runs, that
 * `finally` runs exactly once, and that the exception which ultimately propagates is
 * the catch clause's own, not the try body's original exception.
 */
class ResourceCatchThrowsFinallySpec extends AbstractShellSpec {
  private val setup =
    "import { java.lang.AutoCloseable; }\n" +
      "class Log { public: static var s: String = \"\" }\n" +
      "class R conforms AutoCloseable { public: def this { }\n def close(): void { Log::s = Log::s + \"close\" } }\n"

  it("closes the resource, then runs the throwing catch, then finally exactly once") {
    assert(Shell.Success("close|catch|fin|outer") == shell.run(
      setup +
        "def f(): void { try (val r = new R()) { throw new IllegalArgumentException(\"x\") } catch e: IllegalArgumentException { Log::s = Log::s + \"|catch\"\n throw new IllegalStateException(\"y\") } finally { Log::s = Log::s + \"|fin\" } }\n" +
        "def main(args: String[]): String { try { f() } catch e: IllegalStateException { Log::s = Log::s + \"|outer\" }\n return Log::s }",
      "None", Array()))
  }
}
