package onion.compiler.tools

import onion.tools.Shell

/**
 * `[1, 2] + [3]` was E0001 "operator + is not applicable for type List[Int], List[Int]";
 * joining two lists needed `Colls::concat(a, b)`. `+` on two Lists now returns a new List
 * with the right operand's elements after the left's (neither operand is changed), as in
 * Kotlin and Python, via a builtin `plus(List, List)` extension (`a.plus(b)` works too).
 */
class ListPlusSpec extends AbstractShellSpec {
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

  describe("+ on two Lists") {
    it("concatenates") {
      assert(eval("""return "" + ([1, 2] + [3])""") == Shell.Success("[1, 2, 3]"))
    }
    it("keeps the element type for further calls") {
      assert(eval("""val xs = [1, 2] + [3, 4]
                    |return "" + xs.map { x -> x * 2 }.sum()""".stripMargin) == Shell.Success("20.0"))
    }
    it("leaves both operands unchanged") {
      assert(eval("""val a = [1]
                    |val b = [2]
                    |val c = a + b
                    |return "" + a + b + c""".stripMargin) == Shell.Success("[1][2][1, 2]"))
    }
    it("works as plus() and in +=") {
      assert(eval("""var acc: List[String] = []
                    |acc += ["x"]
                    |acc = acc.plus(["y"])
                    |return "" + acc""".stripMargin) == Shell.Success("[x, y]"))
    }
    it("still concatenates a String with a List") {
      assert(eval("""return "xs=" + [1, 2]""") == Shell.Success("xs=[1, 2]"))
    }
  }
}
