package onion.compiler.tools

import onion.tools.Shell

/** An `Int?` is a boxed `Integer`; it never becomes a `Long?`, so it is a compile error, not a ClassCastException (#2730). */
class NullablePrimitiveBoxMismatchSpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result =
    shell.run(
      s"""
         |record N(a: Int, b: Long?)
         |class Test {
         |public:
         |  static def main(args: String[]): Int {
         |    $body
         |    return 0
         |  }
         |}
         |""".stripMargin, "None", Array())

  describe("a nullable primitive of a different type") {
    it("`val b: Long? = j` with `j: Int?` is rejected at compile time") {
      assert(Shell.Failure(-1) == run("val j: Int? = 3\n    val b: Long? = j"))
    }
    it("a constructor argument `new N(1, x)` with `x: Int?` is rejected") {
      assert(Shell.Failure(-1) == run("val x: Int? = 7\n    val n = new N(1, x)"))
    }
    it("a list element `[j]` for a `List[Long?]` is rejected") {
      assert(Shell.Failure(-1) == run("val j: Int? = 4\n    val ys: List[Long?] = [j]"))
    }
    it("the same primitive still fits") {
      assert(Shell.Success(0) == run("val j: Long? = 3L\n    val b: Long? = j"))
    }
  }
}
