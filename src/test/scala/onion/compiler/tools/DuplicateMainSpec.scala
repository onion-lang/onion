package onion.compiler.tools

import onion.tools.Shell
import onion.compiler.{CompilerConfig, FileInputSource, OnionCompiler}

/**
 * A top-level `def main(args: String[])` must be used as the entry point (it is
 * a public static method on the synthesized top-level class), not collide with a
 * synthesized main. Historically this produced a ClassFormatError, then a
 * spurious E0010; now the user's main is simply used ([#189]).
 */
class DuplicateMainSpec extends AbstractShellSpec {
  describe("top-level def main(args: String[]) collision") {
    it("uses the user's top-level main as the entry point") {
      val result = shell.run(
        """
          |def main(args: String[]): void {
          |  IO::println("user main")
          |}
          |""".stripMargin,
        "DuplicateMain.on",
        Array()
      )
      // The user's main is the entry point; a void main returns null.
      assert(Shell.Success(null) == result)
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
