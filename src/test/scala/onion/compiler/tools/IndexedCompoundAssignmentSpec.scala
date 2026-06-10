package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for compound assignment to indexed lvalues (issue #122) and the
 * underlying fix: xs[i] on a generic collection is typed with the
 * specialized element type instead of the erased one.
 */
class IndexedCompoundAssignmentSpec extends AbstractShellSpec {

  describe("Indexed compound assignment") {
    it("applies += to a list element") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs = [10, 20, 30]
          |    xs[1] += 5
          |    return xs.get(1) as Integer
          |  }
          |}
          |""".stripMargin,
        "ListCompoundAssign.on",
        Array()
      )
      assert(Shell.Success(25) == result)
    }

    it("applies *= to an array element") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val arr = new Int[3]
          |    arr[0] = 7
          |    arr[0] *= 6
          |    return arr[0]
          |  }
          |}
          |""".stripMargin,
        "ArrayCompoundAssign.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("types indexed reads with the specialized element type") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs = [40, 1]
          |    val a: Int = xs[0]
          |    return a + xs[1]
          |  }
          |}
          |""".stripMargin,
        "IndexedReadElementType.on",
        Array()
      )
      assert(Shell.Success(41) == result)
    }
  }
}
