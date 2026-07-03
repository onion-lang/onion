package onion.compiler.tools

import onion.tools.Shell

/**
 * A local variable declared with a type annotation that references itself in its
 * own initializer (`val x: T = x`) is a clean "variable not found" error, not a
 * VerifyError from loading an uninitialized slot. Matches the type-inferred form,
 * which already reported it.
 */
class SelfReferentialInitializerSpec extends AbstractShellSpec {
  it("rejects a self-referential typed val initializer") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { val a: String = a\n IO::println(a) }", "None", Array()))
  }
  it("rejects a self-referential typed var initializer") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { var y: Int = y\n IO::println(y) }", "None", Array()))
  }
  it("still allows referencing a prior variable") {
    assert(Shell.Success(6) == shell.run(
      "def main(args: String[]): Int { val x: Int = 5\n val y: Int = x + 1\n return y }", "None", Array()))
  }
}
