package onion.compiler.tools

import onion.tools.Shell

/**
 * A labeled `break`/`continue` that exits through *multiple* nested
 * `synchronized(...) { ... }` blocks must release every monitor it unwinds
 * through (the JVM requires monitor count balance at method exit, or a
 * missed `monitorexit` surfaces as `IllegalMonitorStateException`/a hang) --
 * the same "every early-exit path must run the pending finally actions"
 * guarantee already covered for a single `synchronized` block and a bare
 * `break`/`continue`/`return` (`SynchronizedExitSpec`), and for nested
 * try-with-resources unwound by a labeled break/continue, but not yet
 * exercised across nested `synchronized` blocks or a `synchronized`/
 * try-with-resources mix.
 */
class LabeledBreakContinueThroughNestedSynchronizedSpec extends AbstractShellSpec {
  private val log = "class Log { public: static var s: String = \"\" }\n"

  it("releases both monitors when a labeled break unwinds through nested synchronized blocks") {
    assert(Shell.Success("x=1|reacquired") == shell.run(
      log +
        "def f(): String {\n" +
        "  var out: String = \"\"\n" +
        "  outer: while true {\n" +
        "    synchronized(\"A\") {\n" +
        "      synchronized(\"B\") {\n" +
        "        out = out + \"x=1\"\n" +
        "        break outer\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "  synchronized(\"A\") { synchronized(\"B\") { out = out + \"|reacquired\" } }\n" +
        "  return out\n" +
        "}\n" +
        "def main(args: String[]): String { return f() }",
      "None", Array()))
  }

  it("releases both monitors on every iteration when a labeled continue unwinds through nested synchronized blocks") {
    assert(Shell.Success("x=1,x=3,reacquired") == shell.run(
      "def f(): String {\n" +
        "  var out: String = \"\"\n" +
        "  outer: foreach x: Int in [1,2,3] {\n" +
        "    synchronized(\"A\") {\n" +
        "      synchronized(\"B\") {\n" +
        "        if x == 2 { continue outer }\n" +
        "        out = out + \"x=\" + x + \",\"\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "  synchronized(\"A\") { synchronized(\"B\") { out = out + \"reacquired\" } }\n" +
        "  return out\n" +
        "}\n" +
        "def main(args: String[]): String { return f() }",
      "None", Array()))
  }

  it("closes the resource and releases the monitor when a labeled break unwinds through synchronized nested inside try-with-resources") {
    assert(Shell.Success("close(r)|reacquired") == shell.run(
      "import { java.lang.AutoCloseable; }\n" +
        log +
        "class R conforms AutoCloseable {\n" +
        "  val name: String\n" +
        "public:\n" +
        "  def this(n: String) { name = n }\n" +
        "  def close(): void { Log::s = Log::s + \"close(\" + name + \")\" }\n" +
        "}\n" +
        "def f(): void {\n" +
        "  outer: while true {\n" +
        "    try (val r = new R(\"r\")) {\n" +
        "      synchronized(\"LOCK\") {\n" +
        "        break outer\n" +
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

  it("closes the resource and releases the monitor when a labeled break unwinds through try-with-resources nested inside synchronized") {
    assert(Shell.Success("close(r)|reacquired") == shell.run(
      "import { java.lang.AutoCloseable; }\n" +
        log +
        "class R conforms AutoCloseable {\n" +
        "  val name: String\n" +
        "public:\n" +
        "  def this(n: String) { name = n }\n" +
        "  def close(): void { Log::s = Log::s + \"close(\" + name + \")\" }\n" +
        "}\n" +
        "def f(): void {\n" +
        "  outer: while true {\n" +
        "    synchronized(\"LOCK\") {\n" +
        "      try (val r = new R(\"r\")) {\n" +
        "        break outer\n" +
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
