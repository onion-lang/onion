package onion.compiler.tools

import onion.tools.Shell

/**
 * `val (yes, no) = xs.partition { ... }` -- the way partition's two-list result is meant
 * to be taken apart -- was E0047 "not a record type", with follow-on E0002s for every
 * name. A List now destructures positionally: the i-th name binds element i, typed as the
 * List's element type. A list shorter than the names throws IndexOutOfBoundsException at
 * run time; extra elements are ignored.
 */
class ListDestructuringSpec extends AbstractShellSpec {
  private def eval(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |    $body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("destructuring a List") {
    it("takes partition's result apart, each half typed as a List") {
      assert(eval("""val (big, small) = [5, 1, 7, 2].partition { x -> x > 3 }
                    |return "" + big + small + big.sum()""".stripMargin) == Shell.Success("[5, 7][1, 2]12.0"))
    }
    it("types each name as the element type") {
      assert(eval("""val (first, second) = ["ab", "c"]
                    |return "" + (first.length() + second.length())""".stripMargin) == Shell.Success("3"))
    }
    it("ignores extra elements") {
      assert(eval("""val (a, b) = [1, 2, 3]
                    |return "" + (a + b)""".stripMargin) == Shell.Success("3"))
    }
    it("throws IndexOutOfBoundsException for a list shorter than the names") {
      assert(eval("""try {
                    |  val (a, b) = [1]
                    |  return "no"
                    |} catch e: IndexOutOfBoundsException { return "short" }""".stripMargin) == Shell.Success("short"))
    }
    it("works with var, allowing reassignment") {
      assert(eval("""var (a, b) = ["x", "y"]
                    |a = a + b
                    |return a""".stripMargin) == Shell.Success("xy"))
    }
    it("still rejects a non-List, non-record value") {
      assert(eval("""val (a, b) = "ab"
                    |return "" """.stripMargin) == Shell.Failure(-1))
    }
  }
}
