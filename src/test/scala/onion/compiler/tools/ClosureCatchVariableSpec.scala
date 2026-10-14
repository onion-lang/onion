package onion.compiler.tools

import onion.tools.Shell

/**
 * A `catch` variable read inside a lambda was wrongly classified as a capture of an
 * outer-scope variable of the same name (`CapturedVariableScanner` never recognized a
 * catch-clause argument, or a try-with-resources binding, as a local of the closure's own
 * scope). Boxing is tracked by name for the whole method, so that one misclassification
 * boxed the catch variable without ever creating its box, and the mismatch between the
 * raw exception value stored by the catch handler and the box read elsewhere failed JVM
 * verification (#2159).
 */
class ClosureCatchVariableSpec extends AbstractShellSpec {
  describe("a catch variable read inside a lambda") {
    it("is not treated as a captured outer variable") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f: () -> String = () -> {
          |      try { throw new Exception("x") } catch e: Exception { return "m=" + e.getMessage() }
          |      return "unreached"
          |    }
          |    return f()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("m=x") == result)
    }

    it("does not corrupt an unrelated outer catch variable of the same name") {
      // The by-name boxing bug marked EVERY local named "e" in the method as boxed once
      // any catch variable "e" was (mis)seen as captured inside the lambda -- including a
      // completely separate, later top-level catch clause that is never itself captured.
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f: () -> String = () -> {
          |      try { throw new Exception("inner") } catch e: Exception { return "inner=" + e.getMessage() }
          |      return "unreached"
          |    }
          |    val fromLambda = f()
          |    try {
          |      throw new Exception("outer")
          |    } catch e: Exception {
          |      return fromLambda + " outer=" + e.getMessage()
          |    }
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("inner=inner outer=outer") == result)
    }
  }
}
