package onion.compiler.tools

import onion.tools.Shell

/**
 * A lambda in a subclass that called an inherited protected method of a class in another
 * package compiled, then failed with IllegalAccessError: the closure class is not a
 * subclass of its host (#2910). The call now goes through a synthetic accessor on the host.
 */
class ProtectedCallInLambdaSpec extends AbstractShellSpec {
  describe("a lambda calling an inherited protected Java method") {
    it("removeRange inside a lambda runs") {
      val src =
        """import { java.util.ArrayList }
          |class MyList extends ArrayList[String] {
          |public:
          |  def run(): Int {
          |    add("a"); add("b"); add("c")
          |    val f: () -> Int = () -> { removeRange(0, 1); size() }
          |    return f()
          |  }
          |}
          |def main(args: String[]): Int { return new MyList().run() }
          |""".stripMargin
      assert(shell.run(src, "None", Array()) == Shell.Success(2))
    }
    it("works through this., from a nested lambda, and with a value result") {
      val src =
        """import { java.util.ArrayList }
          |class MyList extends ArrayList[String] {
          |public:
          |  def run(): Int {
          |    add("a"); add("b"); add("c"); add("d")
          |    val outer: () -> Int = () -> {
          |      val inner: () -> Int = () -> { this.removeRange(0, 1); size() }
          |      inner() * 10 + size()
          |    }
          |    return outer()
          |  }
          |}
          |def main(args: String[]): Int { return new MyList().run() }
          |""".stripMargin
      assert(shell.run(src, "None", Array()) == Shell.Success(33))
    }
    it("a protected method of an Onion class in the same package still calls directly") {
      val src =
        """class Base {
          |protected:
          |  def secret(): Int = 7
          |}
          |class Sub extends Base {
          |public:
          |  def run(): Int {
          |    val f: () -> Int = () -> secret()
          |    return f()
          |  }
          |}
          |def main(args: String[]): Int { return new Sub().run() }
          |""".stripMargin
      assert(shell.run(src, "None", Array()) == Shell.Success(7))
    }
  }
}
