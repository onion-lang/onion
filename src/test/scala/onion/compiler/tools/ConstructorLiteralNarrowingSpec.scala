package onion.compiler.tools

import onion.tools.Shell

/**
 * Constant narrowing (issue #374): an integer literal (or its negation) that
 * fits a Byte/Short/Char parameter's range should be accepted at a
 * constructor call site, exactly as it already is for `val b: Byte = 100`.
 */
class ConstructorLiteralNarrowingSpec extends AbstractShellSpec {
  describe("constant narrowing at a constructor call site") {
    it("narrows negative literals to Short/Byte record components") {
      assert(Shell.Success(-7) == shell.run(
        """
          |record All(s: String, sh: Short, by: Byte)
          |def main(args: String[]): Int {
          |  val a = new All("hi", -3, -4)
          |  return (a.sh() as Int) + (a.by() as Int)
          |}
        """.stripMargin, "None", Array()))
    }
    it("narrows a positive literal to a Byte constructor parameter") {
      assert(Shell.Success(100) == shell.run(
        """
          |class C {
          |  public: val b: Byte
          |  def this(b: Byte) { this.b = b }
          |}
          |def main(args: String[]): Int { return new C(100).b as Int }
        """.stripMargin, "None", Array()))
    }
    it("still rejects an out-of-range literal at a constructor call site") {
      assert(Shell.Failure(-1) == shell.run(
        """
          |class C {
          |  public: val b: Byte
          |  def this(b: Byte) { this.b = b }
          |}
          |def main(args: String[]): Int { return new C(200).b as Int }
        """.stripMargin, "None", Array()))
    }
  }
}
