package onion.compiler.tools

import onion.tools.Shell

/**
 * Overloads that differ only in the arity of a function parameter are chosen by the
 * lambda's parameter count, the same way for every call and in any order (#2823).
 * Before, the second call silently reused the first one's overload: a statement
 * vanished from the program, or a valid lambda got E0101.
 */
class LambdaArityOverloadSpec extends AbstractShellSpec {
  private val decls =
    """class A {
      |public:
      |  static def f(x: Int, g: (Int) -> Int): String = "A1:" + g(x)
      |  static def f(x: Int, g: (Int, Int) -> Int): String = "A2:" + g(x, x)
      |}
      |class B {
      |public:
      |  def this { }
      |  def f(x: Int, g: (Int) -> Int): String = "B1:" + g(x)
      |  def f(x: Int, g: (Int, Int) -> Int): String = "B2:" + g(x, x)
      |}""".stripMargin

  private def runMain(body: String): Shell.Result =
    shell.run(
      s"""
         |$decls
         |class Main {
         |public:
         |  static def main(args: String[]): String {
         |    val sb = new StringBuilder()
         |$body
         |    return sb.toString()
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("overloads that differ only in a lambda parameter's arity") {
    it("pick one-then-two parameters for static calls, keeping both statements") {
      val result = runMain(
        """    sb.append(A::f(3) { x -> x + 1 }).append(",")
          |    sb.append(A::f(3) { x, y -> x * y })""".stripMargin)
      assert(Shell.Success("A1:4,A2:9") == result)
    }

    it("pick two-then-one parameters for static calls, keeping both statements") {
      val result = runMain(
        """    sb.append(A::f(3) { x, y -> x * y }).append(",")
          |    sb.append(A::f(3) { x -> x + 1 })""".stripMargin)
      assert(Shell.Success("A2:9,A1:4") == result)
    }

    it("pick by arity when the results are bound to vals") {
      val result = runMain(
        """    val s = A::f(3) { x, y -> x * y }
          |    val t = A::f(3) { x -> x + 1 }
          |    sb.append(s).append(",").append(t)""".stripMargin)
      assert(Shell.Success("A2:9,A1:4") == result)
    }

    it("pick by arity for instance calls too") {
      val result = runMain(
        """    val b = new B()
          |    sb.append(b.f(3) { x -> x + 1 }).append(",")
          |    sb.append(b.f(3) { x, y -> x * y }).append(",")
          |    sb.append(b.f(3) { x -> x + 1 })""".stripMargin)
      assert(Shell.Success("B1:4,B2:9,B1:4") == result)
    }

    it("still reports E0101 for a lambda whose arity fits no overload") {
      val result = runMain("""    sb.append(A::f(3) { x, y, z -> x })""")
      assert(Shell.Failure(-1) == result)
    }
  }
}
