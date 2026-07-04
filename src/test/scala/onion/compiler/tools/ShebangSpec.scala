package onion.compiler.tools

import onion.tools.Shell

/**
 * Issue #262: a `#!` shebang is only honored on the first line of a script.
 * On the first line it is dropped (keeping later line numbers intact); on any
 * other line `#!` is a syntax error rather than being silently skipped.
 */
class ShebangSpec extends AbstractShellSpec {
  it("honors a shebang on the first line") {
    val result = shell.run(
      "#!/usr/bin/env onion\ndef main(args: String[]): Int = 42\n", "Shebang.on", Array())
    assert(Shell.Success(42) == result)
  }

  it("keeps line numbers after a first-line shebang") {
    // A first-line shebang must not shift the reported error line.
    val result = shell.run(
      "#!/usr/bin/env onion\ndef main(args: String[]): Int { val x: Int = \"wrong\"\n return 0 }\n",
      "Shebang.on", Array())
    assert(Shell.Failure(-1) == result)
  }

  it("rejects a #! that is not on the first line") {
    val result = shell.run(
      "def main(args: String[]): Int = 0\n#! not a shebang\n", "Shebang.on", Array())
    assert(Shell.Failure(-1) == result)
  }

  it("compiles a normal program with no shebang") {
    val result = shell.run("def main(args: String[]): Int = 7\n", "Shebang.on", Array())
    assert(Shell.Success(7) == result)
  }
}
