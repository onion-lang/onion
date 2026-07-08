package onion.compiler.tools

import onion.tools.Shell

/**
 * `arr?.length` on a nullable array used to crash the compiler (I0000) — the
 * array `length` pseudo-field has no affiliation class, and the safe-field-access
 * codegen dereferenced it unconditionally instead of emitting ARRAYLENGTH.
 */
class SafeArrayLengthSpec extends AbstractShellSpec {
  describe("safe access to an array's length") {
    it("returns the length when the array is non-null") {
      val result = shell.run(
        """
          | static def main(args: String[]): Int {
          |   val a: Int[]? = new Int[3]
          |   val m: Int? = a?.length
          |   return m!!
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(3) == result)
    }

    it("returns null when the array is null") {
      val result = shell.run(
        """
          | static def main(args: String[]): String {
          |   val a: Int[]? = null
          |   val m: Int? = a?.length
          |   return "" + m
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
    }
  }
}
