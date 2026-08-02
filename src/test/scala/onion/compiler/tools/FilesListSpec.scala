package onion.compiler.tools

import onion.tools.Shell

/**
 * `Files::list` is implemented and documented in docs/reference/stdlib.md
 * right next to `Files::glob`, but unlike `glob`, was never invoked by a
 * `shell.run` test case anywhere in the suite. Added one.
 */
class FilesListSpec extends AbstractShellSpec {
  it("lists directory entries as a sorted List of names") {
    val dir = System.getProperty("java.io.tmpdir") + "/onion-files-list-test"
    val result = shell.run(
      s"""
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    new java.io.File("$dir").mkdirs()
        |    Files::writeText("$dir/b.txt", "y")
        |    Files::writeText("$dir/a.txt", "x")
        |    val found = Files::list("$dir")
        |    Files::delete("$dir/a.txt")
        |    Files::delete("$dir/b.txt")
        |    return found.toString()
        |  }
        |}
        |""".stripMargin,
      "FilesListBasic.on",
      Array()
    )
    assert(Shell.Success("[a.txt, b.txt]") == result)
  }
}
