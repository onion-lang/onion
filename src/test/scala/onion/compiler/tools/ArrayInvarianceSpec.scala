package onion.compiler.tools

import onion.tools.Shell

/**
 * Arrays are covariant only in a REFERENCE component (`String[] <: Object[]`);
 * primitive-component arrays are INVARIANT on the JVM (`int[]` is not a
 * `long[]`). Assigning `int[]` to `long[]` used to be accepted via numeric
 * widening of the element type, then failed at runtime with a ClassCastException
 * (a wrong array checkcast). See issue #310.
 */
class ArrayInvarianceSpec extends AbstractShellSpec {

  describe("primitive arrays are invariant") {
    it("rejects assigning an int[] to a long[] at compile time") {
      val result = shell.run(
        """
          | static def main(args: String[]): void {
          |   val x: Long[] = new Int[2]
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Failure(-1) == result) // was: accepted, then ClassCastException at runtime
    }

    it("still allows a primitive array to itself") {
      val result = shell.run(
        """
          | static def main(args: String[]): Int {
          |   val x: Int[] = new Int[3]
          |   return x.length
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(3) == result)
    }
  }

  describe("reference arrays stay covariant") {
    it("allows a String[] where an Object[] is expected") {
      val result = shell.run(
        """
          | import { java.lang.* }
          | static def main(args: String[]): Int {
          |   val x: Object[] = new String[2]
          |   return x.length
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(2) == result)
    }
  }

  describe("merging incompatible primitive-array branches (#310)") {
    it("types the merge as Object and runs without a wrong array checkcast") {
      val result = shell.run(
        """
          | static def main(args: String[]): String {
          |   val xs = if true { new Int[2] } else { new Long[2] }
          |   return "done"
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("done") == result) // was: ClassCastException [I -> [J
    }
  }
}
