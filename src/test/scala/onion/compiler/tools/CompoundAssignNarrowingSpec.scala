package onion.compiler.tools

import onion.tools.Shell

/**
 * Compound assignment on a byte/short/char local implicitly narrows the result, so
 * `b += 5` works (Java defines `E1 op= E2` as `E1 = (T)(E1 op E2)`). A plain
 * assignment still requires an explicit cast.
 */
class CompoundAssignNarrowingSpec extends AbstractShellSpec {
  it("narrows a Byte compound assignment") {
    assert(Shell.Success(15) == shell.run(
      "def main(args: String[]): Int { var b: Byte = 10\n b += 5\n return (b as Int) }", "None", Array()))
  }
  it("narrows a Short compound assignment through several ops") {
    assert(Shell.Success(600) == shell.run(
      "def main(args: String[]): Int { var s: Short = 100\n s += 200\n s *= 2\n return (s as Int) }", "None", Array()))
  }
  it("wraps a Byte compound assignment like Java") {
    assert(Shell.Success(-56) == shell.run(
      "def main(args: String[]): Int { var b: Byte = 100\n b += 100\n return (b as Int) }", "None", Array()))
  }
  it("still rejects a plain narrowing assignment without a cast") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { var b: Byte = 10\n b = b + 5 }", "None", Array()))
  }
  it("does not affect an Int compound assignment") {
    assert(Shell.Success(15) == shell.run(
      "def main(args: String[]): Int { var x = 10\n x += 5\n return x }", "None", Array()))
  }
}
