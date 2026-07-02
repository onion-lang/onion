package onion.compiler.tools

import onion.tools.Shell

/**
 * A safe call on a nullable primitive (o?.method() where o: Int?/Double?/...)
 * compiles and runs — it used to crash the compiler (I0000).
 */
class SafeCallNullablePrimitiveSpec extends AbstractShellSpec {
  describe("safe call on a nullable primitive") {
    it("returns the fallback when the receiver is null") {
      assert(Shell.Success("none") == shell.run(
        "def main(args: String[]): String { val o: Int? = null\n return o?.toString() ?: \"none\" }", "None", Array()))
    }
    it("calls through when the receiver is non-null") {
      assert(Shell.Success("42") == shell.run(
        "def main(args: String[]): String { val o: Int? = 42\n return o?.toString() ?: \"none\" }", "None", Array()))
    }
    it("works for Double? and Boolean?") {
      assert(Shell.Success("3.5") == shell.run(
        "def main(args: String[]): String { val d: Double? = 3.5\n return d?.toString() ?: \"n\" }", "None", Array()))
      assert(Shell.Success("true") == shell.run(
        "def main(args: String[]): String { val b: Boolean? = true\n return b?.toString() ?: \"n\" }", "None", Array()))
    }
  }
}
