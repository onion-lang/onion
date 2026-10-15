package onion.compiler.tools

import onion.tools.Shell

/**
 * A try-with-resources binding read inside a lambda body is a local of that lambda, not a
 * captured outer variable -- the same gap #2159/#2162 fixed for a `catch` binding, but for
 * `try (val r = ...)` (#2164).
 */
class ClosureTryResourceVariableSpec extends AbstractShellSpec {
  describe("a try-with-resources binding used inside a lambda body") {
    it("reads the resource without a VerifyError") {
      val result = shell.run(
        """
          |class Res conforms java.lang.AutoCloseable {
          |public:
          |  def close(): void { }
          |  def tag(): String = "r"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f: () -> String = () -> {
          |      try (val r = new Res()) {
          |        return "x=" + r.tag()
          |      }
          |    }
          |    return f()
          |  }
          |}
          |""".stripMargin, "ClosureTryResourceVariable.on", Array())
      assert(result == Shell.Success("x=r"))
    }

    it("works when the same name is also used as a resource outside the lambda") {
      val result = shell.run(
        """
          |class Res(val tag: String) conforms java.lang.AutoCloseable {
          |public:
          |  def close(): void { }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f: () -> String = () -> {
          |      try (val r = new Res("in")) {
          |        return r.tag
          |      }
          |    }
          |    val fromLambda = f()
          |    try (val r = new Res("out")) {
          |      return fromLambda + "/" + r.tag
          |    }
          |  }
          |}
          |""".stripMargin, "ClosureTryResourceVariableShared.on", Array())
      assert(result == Shell.Success("in/out"))
    }
  }
}
