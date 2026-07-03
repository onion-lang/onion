package onion.compiler.tools

import onion.tools.Shell

/**
 * A `synchronized` block releases its monitor on every exit path, including a
 * `return`/`break` out of the body (it used to skip monitorExit -> the codegen
 * threw IllegalMonitorStateException). A synchronized body that always returns is
 * also recognized as a terminal return (no false E0067).
 */
class SynchronizedExitSpec extends AbstractShellSpec {
  it("returns from inside a synchronized body without a monitor error") {
    assert(Shell.Success(42) == shell.run(
      "def f(): Int { synchronized(\"L\") { return 42 } }\ndef main(args: String[]): Int { return f() }", "None", Array()))
  }
  it("recognizes a synchronized-return as terminal (no false E0067)") {
    assert(Shell.Success(1) == shell.run(
      "def f(x: Int): Int { synchronized(\"L\") { if x > 0 { return 1 } else { return 2 } } }\ndef main(args: String[]): Int { return f(5) }", "None", Array()))
  }
  it("still requires a return when the synchronized body does not return") {
    assert(Shell.Failure(-1) == shell.run(
      "def f(): Int { synchronized(\"L\") { IO::println(\"x\") } }\ndef main(args: String[]): void { IO::println(f()) }", "None", Array()))
  }
  it("releases the monitor on break out of a synchronized in a loop") {
    assert(Shell.Success("done") == shell.run(
      "def main(args: String[]): String { foreach x: Int in [1,2,3] { synchronized(\"L\") { if x == 2 { break } } }\n return \"done\" }", "None", Array()))
  }
}
