package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression tests for issue #132: closures referencing the enclosing
 * instance (unqualified calls to top-level functions, which compile to
 * instance methods of the script class) need a generated this$0 field.
 */
class OuterThisCaptureSpec extends AbstractShellSpec {

  describe("Closures capturing the enclosing instance") {
    it("calls an instance method of the enclosing class from a lambda") {
      val result = shell.run(
        """
          |class Test {
          |  def greet(name: String): String { return "Hello, " + name }
          |public:
          |  def run(): String {
          |    val f = (x: String) -> this.greet(x)
          |    return f("world")
          |  }
          |  def this {}
          |  static def main(args: String[]): String {
          |    return new Test().run()
          |  }
          |}
          |""".stripMargin,
        "OuterThisLambda.on",
        Array()
      )
      assert(Shell.Success("Hello, world") == result)
    }

    it("calls unqualified enclosing methods from nested lambdas") {
      val result = shell.run(
        """
          |class Test {
          |  def shout(s: String): String { return s + "!" }
          |public:
          |  def run(): String {
          |    val outer = (a: String) -> {
          |      val inner = (b: String) -> shout(a + b)
          |      return inner("world")
          |    }
          |    return outer("hello ")
          |  }
          |  def this {}
          |  static def main(args: String[]): String {
          |    return new Test().run()
          |  }
          |}
          |""".stripMargin,
        "NestedOuterThis.on",
        Array()
      )
      assert(Shell.Success("hello world!") == result)
    }
  }
}
