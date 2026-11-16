package onion.compiler.tools

import onion.tools.Shell

/** #3041: a bare `return` in a lambda whose result type is inferred is a `void` early exit. */
class LambdaBareReturnSpec extends AbstractShellSpec {
  describe("bare return in a lambda with an inferred result") {
    it("exits early from a side-effect lambda") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var total = 0
          |    val add = (x: Int) -> { if x < 0 { return } total += x; val done = 1 }
          |    add(5)
          |    add(-3)
          |    add(2)
          |    val h = (x: Int) -> { if x < 0 { return } }
          |    h(1)
          |    h(-1)
          |    return total.toString()
          |  }
          |}
          |""".stripMargin,
        "LambdaBareReturn.on",
        Array()
      )
      assert(Shell.Success("7") == result)
    }

    it("still rejects a bare return against a value-returning target") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f: Function1[Int, String] = (x: Int) -> { if x < 0 { return } "a" }
          |    return f(1)
          |  }
          |}
          |""".stripMargin,
        "LambdaBareReturnTarget.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
