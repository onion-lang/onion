package onion.compiler.tools

import onion.tools.Shell
import onion.compiler.{CompilerConfig, FileInputSource, OnionCompiler}

/**
 * Regression tests for top-level `def main(args: String[])` causing a
 * ClassFormatError (duplicate method name) instead of a clean semantic error.
 */
class DuplicateMainSpec extends AbstractShellSpec {
  describe("top-level def main(args: String[]) collision") {
    it("reports a semantic error instead of ClassFormatError") {
      val result = shell.run(
        """
          |def main(args: String[]): void {
          |  IO::println("user main")
          |}
          |""".stripMargin,
        "DuplicateMain.on",
        Array()
      )
      // Should be a Failure (semantic error E0010), NOT an exception/crash
      result match {
        case Shell.Failure(_) => succeed
        case other => fail(s"Expected Failure but got: $other")
      }
    }

    it("does NOT report a duplicate error for zero-arg main") {
      val result = shell.run(
        """
          |def main(): void {
          |  IO::println("ok")
          |}
          |""".stripMargin,
        "ZeroArgNoDuplicate.on",
        Array()
      )
      // Zero-arg main returns void so invoke returns null
      assert(Shell.Success(null) == result)
    }
  }
}
