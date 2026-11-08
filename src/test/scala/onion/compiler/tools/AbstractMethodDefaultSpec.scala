package onion.compiler.tools

import onion.tools.Shell

/** A default on an abstract or interface method can be omitted at a call (#2638). */
class AbstractMethodDefaultSpec extends AbstractShellSpec {

  describe("Defaults on bodiless methods") {
    it("lets a call through an abstract class omit the argument") {
      val result = shell.run(
        """
          |abstract class A {
          |public:
          |  def this {}
          |  abstract def f(n: Int = 5): Int
          |}
          |class B extends A {
          |public:
          |  def this {}
          |  def f(n: Int): Int = n
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a: A = new B()
          |    return "" + a.f() + "," + a.f(7)
          |  }
          |}
          |""".stripMargin,
        "AbstractDefault.on",
        Array()
      )
      assert(Shell.Success("5,7") == result)
    }

    it("lets a call through an interface omit the argument") {
      val result = shell.run(
        """
          |interface I {
          |  def g(n: Int = 3): Int
          |}
          |class C conforms I {
          |public:
          |  def this {}
          |  def g(n: Int): Int = n * 2
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val i: I = new C()
          |    return "" + i.g() + "," + i.g(4)
          |  }
          |}
          |""".stripMargin,
        "InterfaceDefault.on",
        Array()
      )
      assert(Shell.Success("6,8") == result)
    }
  }
}
