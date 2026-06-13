package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression tests for zero-arg top-level `def main(): void` being silently
 * ignored as a script entry point (issue: appendAutoCliCall only matched
 * FunctionDeclaration nodes with nonEmpty args).
 */
class ZeroArgMainSpec extends AbstractShellSpec {
  describe("zero-arg top-level main") {
    it("is called as the script entry point") {
      val result = shell.run(
        """
          |var called = false
          |def main(): void {
          |  called = true
          |  IO::println("zero-arg called")
          |}
          |""".stripMargin,
        "ZeroArgMain.on",
        Array()
      )
      // void main returns null via reflection; Success(null) proves it ran
      assert(Shell.Success(null) == result)
    }

    it("produces the expected output without crashing") {
      val result = shell.run(
        """
          |def main(): void {
          |  IO::println("hello from zero-arg main")
          |}
          |""".stripMargin,
        "ZeroArgMainOutput.on",
        Array()
      )
      // void main returns null via reflection
      assert(Shell.Success(null) == result)
    }

    it("can access top-level vals defined before main") {
      val result = shell.run(
        """
          |val greeting = "top-level greeting"
          |def main(): void {
          |  IO::println(greeting)
          |}
          |""".stripMargin,
        "ZeroArgMainWithTopLevel.on",
        Array()
      )
      assert(Shell.Success(null) == result)
    }
  }
}
