package onion.compiler.tools

import onion.tools.Shell

/**
 * Constant narrowing at a method call site: an integer literal (or its
 * negation) that fits a Byte/Short/Char parameter's range should be accepted
 * exactly as it already is for a constructor call site (issue #374) and for
 * plain assignment (`val b: Byte = 100`).
 */
class MethodLiteralNarrowingSpec extends AbstractShellSpec {
  describe("constant narrowing at a method call site") {
    it("narrows negative literals to Short/Byte top-level function parameters") {
      assert(Shell.Success(-7) == shell.run(
        """
          |def takesShort(x: Short): Int { return x as Int }
          |def takesByte(x: Byte): Int { return x as Int }
          |def main(args: String[]): Int { return takesShort(-3) + takesByte(-4) }
        """.stripMargin, "None", Array()))
    }
    it("narrows a positive literal to a Byte instance method parameter") {
      assert(Shell.Success(100) == shell.run(
        """
          |class C {
          |public:
          |  def takesByte(b: Byte): Int { return b as Int }
          |}
          |def main(args: String[]): Int { return new C().takesByte(100) }
        """.stripMargin, "None", Array()))
    }
    it("still rejects an out-of-range literal at a method call site") {
      assert(Shell.Failure(-1) == shell.run(
        """
          |def takesByte(b: Byte): Int { return b as Int }
          |def main(args: String[]): Int { return takesByte(200) }
        """.stripMargin, "None", Array()))
    }
  }
}
