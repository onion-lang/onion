package onion.compiler.tools

import onion.tools.Shell

/**
 * A top-level main with required leading scalar parameters and a trailing
 * String[] collects the remaining arguments into the array (auto-CLI), instead
 * of silently not running. The auto-CLI entry discards main's return value, so
 * these tests verify the collected values by throwing inside main on a mismatch
 * (a clean run therefore yields Shell.Success(null)).
 */
class ArrayMainAutoCliSpec extends AbstractShellSpec {
  private def prog(expectedCount: Int): String =
    s"""
       |def main(cmd: String, files: String[]): void {
       |  if cmd != "build" { throw new RuntimeException("cmd=" + cmd) }
       |  if files.length != $expectedCount { throw new RuntimeException("n=" + files.length) }
       |}
       |""".stripMargin
  describe("array-parameter main auto-CLI") {
    it("collects trailing arguments into the String[] rest") {
      assert(Shell.Success(null) == shell.run(prog(2), "None", Array("build", "a.txt", "b.txt")))
    }
    it("allows an empty rest") {
      assert(Shell.Success(null) == shell.run(prog(0), "None", Array("build")))
    }
    // The missing-required-argument path calls System.exit via onion.Cli, so it
    // is exercised through the CLI rather than in-process here.
  }
}
