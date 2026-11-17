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

    // The annotation follows the relation `as` uses: unbox then widen, and a cast
    // between an interface and a non-final class, both still compile.
    it("still accepts a widening over a range (Long over 1..3)") {
      assert(Shell.Success(0) == run(
        "    foreach i: Long in 1..3 { IO::println(i) }"))
    }

    it("still accepts a widening over boxed elements (Double over List[Int])") {
      assert(Shell.Success(0) == run(
        """    val ints: List[Int] = [1, 2]
          |    foreach d: Double in ints { IO::println(d) }""".stripMargin))
    }

    it("still accepts an interface over a non-final class (Runnable over List[Base])") {
      val result = shell.run(
        """
          |class Base { public: def this {} }
          |class Sub extends Base conforms Runnable { public: def this {} def run(): void { IO::println("run") } }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val lb: List[Base] = [new Sub()]
          |    foreach r: Runnable in lb { r.run() }
          |    val lr: List[Runnable] = [new Sub()]
          |    foreach b: Base in lr { IO::println("base") }
          |    return 0
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(0) == result)
    }

    it("rejects a final class with an unrelated element type (String over List[Int])") {
      assert(Shell.Failure(-1) == run(
        "    val ints: List[Int] = [1]\n    foreach s: String in ints { IO::println(s) }"))
    }
  }
}
