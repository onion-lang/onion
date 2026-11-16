package onion.compiler.tools

import onion.tools.Shell

// #3105: a call with named arguments to a method declared later in the class was
// E0043 "unknown parameter name", because the later method's names were not known yet.
class NamedArgumentLaterMethodSpec extends AbstractShellSpec {
  private def run(body: String) = shell.run(body, "None", Array())

  describe("Named arguments to a method declared later in the class") {
    it("binds a static call by name") {
      assert(Shell.Success(41) == run(
        """class C {
          |public:
          |  static def a(k: Int): Int = b(y = 1, x = k)
          |  static def b(x: Int, y: Int): Int = x * 10 + y
          |  static def main(args: String[]): Int = a(4)
          |}
          |""".stripMargin))
    }

    it("binds a qualified call and a block body by name") {
      assert(Shell.Success(41) == run(
        """class C {
          |public:
          |  static def a(k: Int): Int { return C::b(y = 1, x = k) }
          |  static def b(x: Int, y: Int): Int = x * 10 + y
          |  static def main(args: String[]): Int = a(4)
          |}
          |""".stripMargin))
    }

    it("binds an instance call by name") {
      assert(Shell.Success(41) == run(
        """class C {
          |public:
          |  def a(k: Int): Int = b(y = 1, x = k)
          |  def b(x: Int, y: Int): Int = x * 10 + y
          |  static def main(args: String[]): Int = new C().a(4)
          |}
          |""".stripMargin))
    }

    it("still rejects an unknown name") {
      assert(Shell.Failure(-1) == run(
        """class C {
          |public:
          |  static def a(k: Int): Int = b(z = 1, x = k)
          |  static def b(x: Int, y: Int): Int = x * 10 + y
          |  static def main(args: String[]): Int = a(4)
          |}
          |""".stripMargin))
    }
  }
}
