package onion.compiler.tools

import onion.tools.Shell

// #3110: named arguments to a callable declared later outside one class body (a top-level
// function, a later class, a record, enum or interface method) were E0043 "unknown parameter
// name", because the callee's parameter names were not known until its own body was typed.
class NamedArgumentForwardDeclarationSpec extends AbstractShellSpec {
  private def run(body: String) = shell.run(body, "None", Array())

  describe("Named arguments to a callable declared later") {
    it("binds a top-level function by name") {
      assert(Shell.Success(41) == run(
        """def a(k: Int): Int = b(y = 1, x = k)
          |def b(x: Int, y: Int): Int = x * 10 + y
          |def main(args: String[]): Int = a(4)
          |""".stripMargin))
    }

    it("binds a static method of a class declared later in the file") {
      assert(Shell.Success(41) == run(
        """class A { public: static def main(args: String[]): Int = B::b(y = 1, x = 4) }
          |class B { public: static def b(x: Int, y: Int): Int = x * 10 + y }
          |""".stripMargin))
    }

    it("binds an instance method of a class declared later in the file") {
      assert(Shell.Success(41) == run(
        """class A { public: static def main(args: String[]): Int = new B().b(y = 1, x = 4) }
          |class B { public: def this {} def b(x: Int, y: Int): Int = x * 10 + y }
          |""".stripMargin))
    }

    it("binds a later method in a record body") {
      assert(Shell.Success(41) == run(
        """record R(v: Int) {
          |public:
          |  def a(): Int = b(y = 1, x = v)
          |  def b(x: Int, y: Int): Int = x * 10 + y
          |}
          |def main(args: String[]): Int = new R(4).a()
          |""".stripMargin))
    }

    it("binds a later method in an enum body") {
      assert(Shell.Success(41) == run(
        """enum E {
          |  ONE
          |public:
          |  def a(k: Int): Int = b(y = 1, x = k)
          |  def b(x: Int, y: Int): Int = x * 10 + y
          |}
          |def main(args: String[]): Int = E::ONE.a(4)
          |""".stripMargin))
    }

    it("binds a later interface default method") {
      assert(Shell.Success(41) == run(
        """interface I {
          |  def a(k: Int): Int = b(y = 1, x = k)
          |  def b(x: Int, y: Int): Int = x * 10 + y
          |}
          |class C conforms I { public: def this {} }
          |def main(args: String[]): Int = new C().a(4)
          |""".stripMargin))
    }

    it("still rejects an unknown name on a later top-level function") {
      assert(Shell.Failure(-1) == run(
        """def a(k: Int): Int = b(z = 1, x = k)
          |def b(x: Int, y: Int): Int = x * 10 + y
          |def main(args: String[]): Int = a(4)
          |""".stripMargin))
    }
  }
}
