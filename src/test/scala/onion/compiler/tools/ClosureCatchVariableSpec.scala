package onion.compiler.tools

import onion.tools.Shell

/**
 * A `catch` binding read inside a lambda body is a local of that lambda, not a captured outer
 * variable (#2159); a lambda inside a `catch` block can read and assign its binding (#2479).
 */
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

  describe("a lambda inside a catch block that captures the catch variable (#2479)") {
    def run(body: String, file: String): Shell.Result = shell.run(
      s"""
         |class Test {
         |public:
         |  static def call(f: (Int) -> String): String = f(1)
         |  static def main(args: String[]): String {
         |    $body
         |    return "none"
         |  }
         |}
         |""".stripMargin, file, Array())

    it("reads the exception in a plain try/catch") {
      val result = run("""try { throw new Exception("x") } catch e: Exception { return call { q -> e.getMessage() } }""", "CatchCapturedPlain.on")
      assert(result == Shell.Success("x"))
    }

    it("reads the exception in a try/catch/finally") {
      val result = run(
        """var log = ""
          |    try { throw new Exception("x") } catch e: Exception { log = call { q -> e.getMessage() } } finally { log = log + "/f" }
          |    return log""".stripMargin, "CatchCapturedFinally.on")
      assert(result == Shell.Success("x/f"))
    }

    it("reads the exception in a try-with-resources") {
      val result = run(
        """try (val r = new java.io.StringReader("s")) { throw new Exception("x") } catch e: Exception { return call { q -> e.getMessage() } }""",
        "CatchCapturedResources.on")
      assert(result == Shell.Success("x"))
    }

    it("sees an assignment the lambda makes to the catch variable") {
      val result = run(
        """try { throw new Exception("x") } catch e: Exception {
          |      val f: () -> String = () -> { e = new Exception("y"); return "" }
          |      f()
          |      return e.getMessage()
          |    }""".stripMargin, "CatchCapturedAssigned.on")
      assert(result == Shell.Success("y"))
    }

    it("reads the exception of a try inside an outer lambda") {
      val result = run(
        """val g: () -> String = () -> {
          |      try { throw new Exception("x") } catch e: Exception { return call { q -> e.getMessage() } }
          |      return "none"
          |    }
          |    return g()""".stripMargin, "CatchCapturedNested.on")
      assert(result == Shell.Success("x"))
    }
  }
}
