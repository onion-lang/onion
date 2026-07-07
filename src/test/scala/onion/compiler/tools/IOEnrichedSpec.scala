package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for the line-oriented IO helpers added to `onion.IO` (auto-imported):
 * printLines, printAll, and flush. These are side-effecting `void` methods, so
 * the tests verify they compile and run cleanly (returning a computed value).
 * The stdin readers (readLines/eachLine/tryReadLong) are covered by piped
 * smoke tests rather than the shell harness, which does not feed stdin.
 */
class IOEnrichedSpec extends AbstractShellSpec {

  private def runInt(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "IOEnriched.on", Array()))
  }

  describe("enriched IO output helpers") {
    it("printLines prints each element and runs cleanly") {
      runInt(
        "val xs = [10, 20, 30]\n" +
        "IO::printLines(xs)\n" +
        "IO::flush()\n" +
        "return xs.size()",
        Shell.Success(3))
    }

    it("printAll prints each argument and runs cleanly") {
      runInt(
        "IO::printAll(\"a\", \"b\", \"c\")\n" +
        "return 42",
        Shell.Success(42))
    }
  }
}
