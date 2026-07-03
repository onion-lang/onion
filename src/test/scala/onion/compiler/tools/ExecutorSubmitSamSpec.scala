package onion.compiler.tools

import onion.tools.Shell

/**
 * A lambda passed to an overloaded Java SAM target (ExecutorService.submit,
 * which accepts both Runnable and Callable) is disambiguated by the lambda's
 * body: a value-returning lambda binds Callable, a void one binds Runnable.
 * This used to fail with E0006 (ambiguous — matches both), a real-world
 * Java-interop pain point; it is now resolved by the bidirectional-inference /
 * closure-return analysis (#232 / #233 / #256). This spec guards against a
 * regression of that behavior.
 */
class ExecutorSubmitSamSpec extends AbstractShellSpec {
  it("binds submit(() -> value) to Callable and returns the computed value") {
    assert(Shell.Success(42) == shell.run(
      "import { java.util.concurrent.* }\ndef main(args: String[]): Int { val ex = Executors::newSingleThreadExecutor()\n val f = ex.submit(() -> 6 * 7)\n val r = f.get()\n ex.shutdown()\n return r }", "None", Array()))
  }
  it("binds submit(() -> { void }) to Runnable") {
    assert(Shell.Success("ok") == shell.run(
      "import { java.util.concurrent.* }\ndef main(args: String[]): String { val ex = Executors::newSingleThreadExecutor()\n val f = ex.submit(() -> { val x = 1 })\n f.get()\n ex.shutdown()\n return \"ok\" }", "None", Array()))
  }
  it("supports CompletableFuture::supplyAsync with a value lambda") {
    assert(Shell.Success(7) == shell.run(
      "import { java.util.concurrent.* }\ndef main(args: String[]): Int { return CompletableFuture::supplyAsync(() -> 7).get() }", "None", Array()))
  }
}
