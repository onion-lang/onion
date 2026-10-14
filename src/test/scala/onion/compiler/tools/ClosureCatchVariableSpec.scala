package onion.compiler.tools

import onion.tools.Shell

/** A `catch` binding read inside a lambda body is a local of that lambda, not a captured outer variable (#2159). */
class ClosureCatchVariableSpec extends AbstractShellSpec {
  describe("a catch variable used inside a lambda body") {
    it("reads the exception without a VerifyError") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f: () -> String = () -> {
          |      try { throw new Exception("x") } catch e: Exception { return "m=" + e.getMessage() }
          |      return "none"
          |    }
          |    return f()
          |  }
          |}
          |""".stripMargin, "ClosureCatchVariable.on", Array())
      assert(result == Shell.Success("m=x"))
    }

    it("works when the same name is also caught outside the lambda") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f: () -> String = () -> {
          |      try { throw new Exception("in") } catch e: Exception { return e.getMessage() }
          |      return "none"
          |    }
          |    try { throw new Exception("out") } catch e: Exception { return f() + "/" + e.getMessage() }
          |    return "none"
          |  }
          |}
          |""".stripMargin, "ClosureCatchVariableShared.on", Array())
      assert(result == Shell.Success("in/out"))
    }
  }
}
