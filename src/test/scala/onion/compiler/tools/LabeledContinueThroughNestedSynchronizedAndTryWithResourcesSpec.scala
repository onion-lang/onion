package onion.compiler.tools

import onion.tools.Shell

/**
 * A labeled `continue` that unwinds through a *mix* of `synchronized(...) { ... }`
 * and `try (resource) { ... }` must both release the monitor and close the
 * resource, on every iteration, in the right order for whichever is innermost --
 * the same "every early-exit path must run the pending cleanup actions" guarantee
 * already covered for a labeled `break` unwinding through this exact mix
 * (`LabeledBreakContinueThroughNestedSynchronizedSpec`), and for a labeled
 * `continue` unwinding through *either* construct nested purely inside itself
 * (`LabeledBreakContinueThroughNestedSynchronizedSpec` for nested `synchronized`,
 * `LabeledContinueThroughNestedTryWithResourcesSpec` for nested try-with-resources),
 * but not yet exercised for `continue` across the two constructs mixed together.
 */
class LabeledContinueThroughNestedSynchronizedAndTryWithResourcesSpec extends AbstractShellSpec {
  private val log = "class Log { public: static var s: String = \"\" }\n"
  private val autoCloseable = "import { java.lang.AutoCloseable; }\n"
  private val resource =
    "class R conforms AutoCloseable {\n" +
      "  val name: String\n" +
      "public:\n" +
      "  def this(n: String) { name = n }\n" +
      "  def close(): void { Log::s = Log::s + \"close(\" + name + \")\" }\n" +
      "}\n"

  it("closes the resource and releases the monitor on every iteration when a labeled continue unwinds through synchronized nested inside try-with-resources") {
    assert(Shell.Success("close(r)close(r)|reacquired") == shell.run(
      autoCloseable + log + resource +
        "def f(): void {\n" +
        "  outer: foreach x: Int in [1,2] {\n" +
        "    try (val r = new R(\"r\")) {\n" +
        "      synchronized(\"LOCK\") {\n" +
        "        continue outer\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}\n" +
        "def main(args: String[]): String {\n" +
        "  f()\n" +
        "  synchronized(\"LOCK\") { Log::s = Log::s + \"|reacquired\" }\n" +
        "  return Log::s\n" +
        "}",
      "None", Array()))
  }

  it("closes the resource and releases the monitor on every iteration when a labeled continue unwinds through try-with-resources nested inside synchronized") {
    assert(Shell.Success("close(r)close(r)|reacquired") == shell.run(
      autoCloseable + log + resource +
        "def f(): void {\n" +
        "  outer: foreach x: Int in [1,2] {\n" +
        "    synchronized(\"LOCK\") {\n" +
        "      try (val r = new R(\"r\")) {\n" +
        "        continue outer\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}\n" +
        "def main(args: String[]): String {\n" +
        "  f()\n" +
        "  synchronized(\"LOCK\") { Log::s = Log::s + \"|reacquired\" }\n" +
        "  return Log::s\n" +
        "}",
      "None", Array()))
  }
}
