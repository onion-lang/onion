package onion.compiler.tools

import onion.tools.Shell

/**
 * The elvis operator `?:` accepts a nullable right operand: `a ?: b` where both
 * are `T?` yields `T?`, which also makes the chained `a ?: b ?: c` idiom work.
 */
class ElvisNullableRhsSpec extends AbstractShellSpec {
  describe("elvis with a nullable right operand") {
    it("returns the fallback when the left is null (both nullable)") {
      val r = shell.run(
        """def main(args: String[]): String {
          |  val a: String? = null
          |  val b: String? = "B"
          |  val r: String? = a ?: b
          |  return r!!
          |}""".stripMargin, "None", Array())
      assert(Shell.Success("B") == r)
    }
    it("returns the left when it is non-null (both nullable)") {
      val r = shell.run(
        """def main(args: String[]): String {
          |  val a: String? = "A"
          |  val b: String? = null
          |  return (a ?: b)!!
          |}""".stripMargin, "None", Array())
      assert(Shell.Success("A") == r)
    }
    it("supports a right-associative chain of nullable fallbacks") {
      val r = shell.run(
        """def main(args: String[]): String {
          |  val a: String? = null
          |  val b: String? = null
          |  return a ?: (b ?: "X")
          |}""".stripMargin, "None", Array())
      assert(Shell.Success("X") == r)
    }
    it("still yields a non-null result with a non-null fallback") {
      val r = shell.run(
        """def main(args: String[]): String {
          |  val a: String? = null
          |  val r: String = a ?: "def"
          |  return r
          |}""".stripMargin, "None", Array())
      assert(Shell.Success("def") == r)
    }
    it("works for nullable primitives") {
      val r = shell.run(
        """def main(args: String[]): Int {
          |  val n: Int? = null
          |  val m: Int? = 7
          |  return (n ?: m)!!
          |}""".stripMargin, "None", Array())
      assert(Shell.Success(7) == r)
    }
  }
}
