package onion.compiler.tools

import onion.tools.Shell

/**
 * A top-level `main` whose parameter list fits none of the supported shapes —
 * a single `String[]` (argv), all scalars (auto-CLI), or a scalar prefix plus a
 * trailing `String[]` rest collector — used to compile to a SILENT no-op (the
 * body landed on an unreachable overload). It must now be a clean error.
 */
class MainSignatureSpec extends AbstractShellSpec {
  it("rejects main with String[] first and an extra parameter (was a silent no-op)") {
    val result = shell.run(
      """
        | def main(args: String[], flag: Boolean = false): void {
        |   IO::println("BODY RAN")
        | }
      """.stripMargin,
      "None",
      Array("x")
    )
    assert(Shell.Failure(-1) == result)
  }

  it("still accepts the conventional single String[] main") {
    val result = shell.run(
      """
        | def main(args: String[]): String {
        |   return "ok"
        | }
      """.stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success("ok") == result)
  }
}
