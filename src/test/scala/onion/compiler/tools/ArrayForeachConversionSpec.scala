package onion.compiler.tools

import onion.tools.Shell

/**
 * `foreach a: T in array` converts each element to the annotation the way `as`
 * does: widen, narrow, box, unbox (#2984). Each used to fail in code generation
 * (I0000) or in the JVM verifier.
 */
class ArrayForeachConversionSpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result =
    shell.run(
      s"""
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |$body
        |  }
        |}
        |""".stripMargin,
      "None",
      Array()
    )

  describe("foreach with an annotation that differs from the array's element type") {
    it("widens Int[] elements to Long") {
      val result = run(
        """
          |    val xs: Int[] = new Int[2]
          |    xs[0] = 3; xs[1] = 4
          |    var sum: Long = 0L
          |    foreach a: Long in xs { sum = sum + a }
          |    return sum as Int
          |""".stripMargin)
      assert(result == Shell.Success(7))
    }

    it("narrows Long[] elements to Int") {
      val result = run(
        """
          |    val xs: Long[] = new Long[2]
          |    xs[0] = 5L; xs[1] = 6L
          |    var sum = 0
          |    foreach a: Int in xs { sum = sum + a }
          |    return sum
          |""".stripMargin)
      assert(result == Shell.Success(11))
    }

    it("boxes Int[] elements into Object") {
      val result = run(
        """
          |    val xs: Int[] = new Int[2]
          |    xs[0] = 1; xs[1] = 2
          |    var n = 0
          |    foreach o: Object in xs { if o is JInteger { n = n + 1 } }
          |    return n
          |""".stripMargin)
      assert(result == Shell.Success(2))
    }

    it("unboxes Object[] elements into Int") {
      val result = run(
        """
          |    val xs: Object[] = new Object[2]
          |    xs[0] = 8; xs[1] = 9
          |    var sum = 0
          |    foreach a: Int in xs { sum = sum + a }
          |    return sum
          |""".stripMargin)
      assert(result == Shell.Success(17))
    }

    it("keeps a loop with no conversion unchanged") {
      val result = run(
        """
          |    val xs: Int[] = new Int[2]
          |    xs[0] = 2; xs[1] = 5
          |    var sum = 0
          |    foreach a: Int in xs { sum = sum + a }
          |    return sum
          |""".stripMargin)
      assert(result == Shell.Success(7))
    }
  }
}
