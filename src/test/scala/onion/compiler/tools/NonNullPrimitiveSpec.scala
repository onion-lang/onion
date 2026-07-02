package onion.compiler.tools

import onion.tools.Shell

/**
 * The `!!` not-null assertion on a nullable primitive unboxes to the primitive
 * (it was previously typed as the primitive but left the boxed value, a VerifyError).
 */
class NonNullPrimitiveSpec extends AbstractShellSpec {
  describe("!! on a nullable primitive") {
    it("unboxes an Int? to int for return") {
      assert(Shell.Success(5) == shell.run(
        "def main(args: String[]): Int { val n: Int? = 5\n return n!! }", "None", Array()))
    }
    it("unboxes a Double? for arithmetic") {
      assert(Shell.Success(4.5) == shell.run(
        "def main(args: String[]): Double { val d: Double? = 3.5\n return d!! + 1.0 }", "None", Array()))
    }
    it("unboxes a Boolean? for a condition") {
      assert(Shell.Success("yes") == shell.run(
        "def main(args: String[]): String { val b: Boolean? = true\n if b!! { return \"yes\" } else { return \"no\" } }", "None", Array()))
    }
    // A null primitive assertion throws at runtime (NPE); that path is exercised
    // via the CLI rather than in-process here.
  }
}
