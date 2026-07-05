package onion.compiler.tools

import onion.tools.Shell

/**
 * A `void`-typed expression cannot be a collection element/key/value. It must be
 * reported as an ordinary type error rather than crashing the compiler, which it
 * previously did (I0000 "unknown boxed type" when Boxing tried to box `void`).
 * Found by the mutation fuzzer.
 */
class VoidCollectionElementSpec extends AbstractShellSpec {
  it("rejects a void expression as a list element") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { val xs = [IO::println(\"x\")] }", "None", Array()))
  }
  it("rejects a void expression as a map value") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { val m = [\"k\": IO::println(\"v\")] }", "None", Array()))
  }
  it("rejects a void expression as a map key") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { val m = [IO::println(\"k\"): 1] }", "None", Array()))
  }
  it("still accepts a normal list and map") {
    assert(Shell.Success(3) == shell.run(
      "def main(args: String[]): Int { val xs = [1, 2, 3]\n return xs.size() }", "None", Array()))
  }
}
