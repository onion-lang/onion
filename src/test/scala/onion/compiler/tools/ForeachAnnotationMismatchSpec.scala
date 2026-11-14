package onion.compiler.tools

import onion.tools.Shell

class ForeachAnnotationMismatchSpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): Int {
         |$body
         |    return 0
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("an annotated foreach variable must relate to the element type (#2946)") {
    it("rejects Int over String[] at compile time") {
      assert(Shell.Failure(-1) == run(
        "    val xs: String[] = new String[2]\n    foreach a: Int in xs { IO::println(a) }"))
    }

    it("rejects Int over List[String] at compile time") {
      assert(Shell.Failure(-1) == run(
        "    val xs: List[String] = [\"a\", \"b\"]\n    foreach a: Int in xs { IO::println(a) }"))
    }

    it("still accepts boxing (Int over List[Int])") {
      assert(Shell.Success(0) == run(
        """    val boxed: List[Int] = [1, 2]
          |    foreach b: Int in boxed { IO::println(b) }""".stripMargin))
    }

    it("still accepts a checked narrowing (String over List[Object])") {
      assert(Shell.Success(0) == run(
        """    val objs: List[Object] = ["x"]
          |    foreach s: String in objs { IO::println(s) }""".stripMargin))
    }
  }
}
