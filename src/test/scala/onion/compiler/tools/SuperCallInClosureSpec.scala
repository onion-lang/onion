package onion.compiler.tools

import onion.tools.Shell

/**
 * `super.f(..)` inside a lambda used to compile and then fail JVM verification: the
 * `invokespecial` was emitted in the closure's own class (#2636). The enclosing class now gets a
 * synthetic accessor, as in Java, and the closure calls that.
 */
class SuperCallInClosureSpec extends AbstractShellSpec {
  private def run(src: String): Shell.Result = shell.run(src.stripMargin, "None", Array())

  private val base =
    """
      |class A {
      |public:
      |  def this {}
      |  def f(n: Int): Int = n
      |  def d(a: Int, b: Int = 7): Int = a + b
      |  def v(s: String): String = "A." + s
      |}
      |class B extends A {
      |public:
      |  def this {}
      |  override def f(n: Int): Int = n * 10
      |  override def v(s: String): String = "B." + s
      |""".stripMargin

  private def program(body: String, call: String): String =
    base + body + "\n}\nclass Test {\npublic:\n  static def main(args: String[]): String = " + call + "\n}\n"

  describe("super.f() inside a lambda (#2636)") {
    it("calls the superclass's method, not the override") {
      val result = run(program(
        "  def g(): Int {\n    val h: () -> Int = () -> super.f(3)\n    return h()\n  }",
        "\"\" + new B().g()"))
      assert(Shell.Success("3") == result)
    }

    it("works from a lambda nested in a lambda, and in a trailing lambda") {
      val result = run(program(
        "  def g(): Int {\n    val h: () -> Int = () -> { val i: () -> Int = () -> super.f(4); return i() + super.f(5) }\n    return h()\n  }",
        "\"\" + new B().g()"))
      assert(Shell.Success("9") == result)
    }

    it("still fills an omitted default from the superclass") {
      val result = run(program(
        "  def g(): Int {\n    val h: () -> Int = () -> super.d(1) + super.d(1, 2)\n    return h()\n  }",
        "\"\" + new B().g()"))
      assert(Shell.Success("11") == result)
    }

    it("returns a reference-typed result") {
      val result = run(program(
        "  def g(): String {\n    val h: () -> String = () -> super.v(\"x\")\n    return h()\n  }",
        "new B().g()"))
      assert(Shell.Success("A.x") == result)
    }

    it("reads captured locals and parameters of the enclosing method") {
      val result = run(program(
        "  def g(k: Int): Int {\n    val m = 2\n    val h: () -> Int = () -> super.f(k) + m\n    return h()\n  }",
        "\"\" + new B().g(6)"))
      assert(Shell.Success("8") == result)
    }

    it("works in a trait's method, whose accessor lives on an interface") {
      val result = run(
        """
          |trait T {
          |  def g(): String {
          |    val h: () -> String = () -> super.toString()
          |    return h()
          |  }
          |}
          |class C conforms T {
          |public:
          |  def this {}
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String = "" + new C().g().startsWith("C@")
          |}
          |""")
      assert(Shell.Success("true") == result)
    }
  }
}
