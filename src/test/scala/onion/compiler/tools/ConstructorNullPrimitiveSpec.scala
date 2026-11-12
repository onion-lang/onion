package onion.compiler.tools

import onion.tools.Shell

/** `null` never fills a primitive constructor parameter (#2828): it is a compile
  * error, as for a method call, not a run-time null-value crash. */
class ConstructorNullPrimitiveSpec extends AbstractShellSpec {
  private val cls =
    """
      |class A {
      |public:
      |  def this(n: Int) { println("I" + n) }
      |}
    """.stripMargin

  describe("null passed to a primitive constructor parameter") {
    it("is a compile error") {
      assert(Shell.Failure(-1) == shell.run(cls + "\nnew A(null)\n", "None", Array()))
    }
    it("still accepts an Int argument") {
      assert(Shell.Success(0) == shell.run(cls + "\ndef main(args: String[]): Int { new A(1); return 0 }\n", "None", Array()))
    }
    it("still accepts null for a nullable parameter") {
      assert(Shell.Success(0) == shell.run(
        """
          |class B {
          |public:
          |  def this(s: String?) { println("B" + s) }
          |}
          |def main(args: String[]): Int { new B(null); return 0 }
        """.stripMargin, "None", Array()))
    }
  }
}
