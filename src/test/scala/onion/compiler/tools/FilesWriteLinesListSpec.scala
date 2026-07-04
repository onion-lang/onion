package onion.compiler.tools

import onion.tools.Shell

/**
 * Issue #285: `Files::writeLines` accepts a `List` argument, not only a
 * `String[]`, matching `Strings::join` which takes both. The script writes a
 * List of lines to a temp file and reads it back, returning the line count.
 */
class FilesWriteLinesListSpec extends AbstractShellSpec {
  it("writes a List of lines and reads them back") {
    val path = System.getProperty("java.io.tmpdir") + "/onion-writelines-list-285.txt"
    val result = shell.run(
      s"""
        |def main(args: String[]): Int {
        |  val p = "$path"
        |  Files::writeLines(p, ["alpha", "beta", "gamma"])
        |  return Files::readLines(p).length
        |}
        |""".stripMargin,
      "None",
      Array()
    )
    assert(Shell.Success(3) == result)
  }
}
