package onion.compiler.tools

import onion.tools.Shell

/**
 * A parameter that a lambda assigns is one variable with the method, as a captured
 * `var` local is (#2335). It used to be copied into the lambda, so the method kept
 * the old value.
 */
class ClosureAssignedParameterSpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |$body
         |  static def main(args: String[]): Int {
         |    return g(1)
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("a parameter assigned inside a lambda") {
    it("is seen updated by the method") {
      val result = run(
        """  static def g(n: Int): Int {
          |    val f: () -> Int = () -> { n = n + 10; n }
          |    val inner = f()
          |    return n * 1000 + inner
          |  }""".stripMargin)
      assert(Shell.Success(11011) == result)
    }

    it("is shared both ways with a long parameter and a later method write") {
      val result = run(
        """  static def g(n: Long): Int {
          |    val f: () -> Long = () -> { n = n * 2L; n }
          |    n = n + 1L
          |    val a = f()
          |    n = n + 5L
          |    return (a + n) as Int
          |  }""".stripMargin)
      assert(Shell.Success(4 + 9) == result)
    }

    it("works on an instance method with another parameter") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  def g(label: String, n: Int): String {
          |    val f: () -> Int = () -> { n = n + 10; n }
          |    f()
          |    return label + n
          |  }
          |  static def main(args: String[]): String = new Test().g("n=", 1)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("n=11") == result)
    }

    it("leaves a captured but never assigned parameter alone") {
      val result = run(
        """  static def g(n: Int): Int {
          |    val f: () -> Int = () -> n + 1
          |    return f() + n
          |  }""".stripMargin)
      assert(Shell.Success(3) == result)
    }
  }
}
