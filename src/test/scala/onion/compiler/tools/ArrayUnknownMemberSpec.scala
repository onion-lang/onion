package onion.compiler.tools

import onion.tools.Shell

/**
 * An undefined member on an array (anything but `length`/`size`) must be a proper
 * type error, not silently accepted (which miscompiled to invalid bytecode — a
 * java.lang.VerifyError). Found by the mutation fuzzer.
 */
class ArrayUnknownMemberSpec extends AbstractShellSpec {
  it("rejects an undefined array member instead of miscompiling") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { val chars = \"abc\".toCharArray()\n var j: Int = chars.lengt\n IO::println(j) }",
      "None", Array()))
  }
  it("still allows array.length") {
    assert(Shell.Success(3) == shell.run(
      "def main(args: String[]): Int { val chars = \"abc\".toCharArray()\n return chars.length }", "None", Array()))
  }
  it("still allows array.size") {
    assert(Shell.Success(5) == shell.run(
      "def main(args: String[]): Int { val a = new Int[5]\n return a.size }", "None", Array()))
  }
}
