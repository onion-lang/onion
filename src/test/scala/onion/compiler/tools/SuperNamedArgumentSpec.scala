package onion.compiler.tools

import onion.tools.Shell

class SuperNamedArgumentSpec extends AbstractShellSpec {
  private def run(body: String) = shell.run(
    s"""
       |class A {
       |public:
       |  def this {}
       |  def d(a: Int = 1, b: Int = 2, c: Int = 3): String = "" + a + b + c
       |}
       |class B extends A {
       |public:
       |  def this {}
       |  def go(): String = $body
       |}
       |class Main {
       |public:
       |  static def main(args: String[]): String = new B().go()
       |}
       |""".stripMargin,
    "None",
    Array()
  )

  describe("Named arguments on a super call") {
    it("binds by name, as the same call on this does") {
      assert(Shell.Success("153") == run("super.d(b = 5)"))
    }

    it("accepts named arguments out of declaration order") {
      assert(Shell.Success("425") == run("super.d(c = 5, a = 4)"))
    }

    it("mixes positional and named arguments") {
      assert(Shell.Success("925") == run("super.d(9, c = 5)"))
    }

    it("rejects an unknown name") {
      assert(Shell.Failure(-1) == run("super.d(z = 5)"))
    }
  }
}
