package onion.compiler.tools

import onion.tools.Shell

/**
 * A labeled `continue` that unwinds through *multiple* nested `try (resource) { ... }`
 * blocks must still close every resource it unwinds through, in the usual reverse
 * (innermost-first) order, before control reaches the labeled loop's next iteration —
 * the same "every early-exit path is a candidate for resource close" guarantee already
 * covered for a single try-with-resources block and for a labeled `break` unwinding
 * through nested try-with-resources blocks (0.75.0-0.79.0), exercised here for
 * `continue` across two nested try-with-resources statements and an intervening plain
 * loop. And if the close() reached during that unwind itself throws, the resulting
 * exception must still propagate to an enclosing catch (the labeled continue must not
 * be allowed to silently swallow it, nor resume the outer loop), while every
 * already-attempted outer resource is still closed on the way out.
 */
class LabeledContinueThroughNestedTryWithResourcesSpec extends AbstractShellSpec {
  private val log = "class Log { public: static var s: String = \"\" }\n"
  private val autoCloseable = "import { java.lang.AutoCloseable; }\n"

  it("closes both resources in innermost-first order on every iteration when a labeled continue unwinds through nested try-with-resources") {
    assert(Shell.Success("close(r2)close(r1)close(r2)close(r1)") == shell.run(
      autoCloseable + log +
        "class R conforms AutoCloseable {\n" +
        "  val name: String\n" +
        "public:\n" +
        "  def this(n: String) { name = n }\n" +
        "  def close(): void { Log::s = Log::s + \"close(\" + name + \")\" }\n" +
        "}\n" +
        "def f(): void {\n" +
        "  var i: Int = 0\n" +
        "  outer: while i < 2 {\n" +
        "    i = i + 1\n" +
        "    try (val r1 = new R(\"r1\")) {\n" +
        "      while true {\n" +
        "        try (val r2 = new R(\"r2\")) {\n" +
        "          continue outer\n" +
        "        }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}\n" +
        "def main(args: String[]): String { f(); return Log::s }",
      "None", Array()))
  }

  it("still closes the outer resource and propagates the exception when the inner resource's close() fails during a labeled-continue unwind") {
    assert(Shell.Success("close(r2)close(r1)|caught:boom-r2") == shell.run(
      autoCloseable + log +
        "class R conforms AutoCloseable {\n" +
        "  val name: String\n" +
        "  val fails: Boolean\n" +
        "public:\n" +
        "  def this(n: String, f: Boolean) { name = n; fails = f }\n" +
        "  def close(): void {\n" +
        "    Log::s = Log::s + \"close(\" + name + \")\"\n" +
        "    if fails { throw new RuntimeException(\"boom-\" + name) }\n" +
        "  }\n" +
        "}\n" +
        "def f(): void {\n" +
        "  var i: Int = 0\n" +
        "  outer: while i < 2 {\n" +
        "    i = i + 1\n" +
        "    try (val r1 = new R(\"r1\", false)) {\n" +
        "      while true {\n" +
        "        try (val r2 = new R(\"r2\", true)) {\n" +
        "          continue outer\n" +
        "        }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}\n" +
        "def main(args: String[]): String {\n" +
        "  try { f() } catch e: RuntimeException { Log::s = Log::s + \"|caught:\" + e.message() }\n" +
        "  return Log::s\n" +
        "}",
      "None", Array()))
  }
}
