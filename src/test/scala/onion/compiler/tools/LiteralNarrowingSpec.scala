package onion.compiler.tools

import onion.tools.Shell

/**
 * An integer literal (or its negation) that fits the target range narrows to a
 * Byte/Short/Char assignment target, like Java's `byte b = 100`.
 */
class LiteralNarrowingSpec extends AbstractShellSpec {
  describe("integer literal narrowing") {
    it("narrows a positive literal to Byte") {
      assert(Shell.Success(100) == shell.run("def main(args: String[]): Int { val b: Byte = 100\n return b as Int }", "None", Array()))
    }
    it("narrows negative boundary literals to Byte/Short") {
      assert(Shell.Success(-128) == shell.run("def main(args: String[]): Int { val b: Byte = -128\n return b as Int }", "None", Array()))
      assert(Shell.Success(-32768) == shell.run("def main(args: String[]): Int { val s: Short = -32768\n return s as Int }", "None", Array()))
    }
    it("narrows an int literal to Char") {
      assert(Shell.Success('A') == shell.run("def main(args: String[]): Char { val c: Char = 65\n return c }", "None", Array()))
    }
    it("rejects an out-of-range literal") {
      assert(Shell.Failure(-1) == shell.run("def main(args: String[]): Int { val b: Byte = 200\n return b as Int }", "None", Array()))
    }
    it("narrows a Byte field initializer and return") {
      assert(Shell.Success(42) == shell.run(
        "class C { public: var b: Byte\n def this() { b = 7 } }\ndef make(): Byte { return 42 }\ndef main(args: String[]): Int { return (new C().b as Int) + (make() as Int) - 7 }", "None", Array()))
    }
  }
}
