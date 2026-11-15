package onion.compiler.tools

import onion.tools.Shell

/** `Files::glob(dir, "**&#47;*.on")` must also match files directly in `dir` (#2879). */
class FilesGlobSpec extends AbstractShellSpec {
  it("a leading **/ matches zero directories, one and two levels down") {
    val dir = (System.getProperty("java.io.tmpdir") + "/onion-files-glob-2879").replace('\\', '/')
    val result = shell.run(
      s"""
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    Files::mkdirs("$dir/sub/deep")
        |    Files::writeText("$dir/a.on", "")
        |    Files::writeText("$dir/sub/b.on", "")
        |    Files::writeText("$dir/sub/deep/c.on", "")
        |    Files::writeText("$dir/sub/deep/d.txt", "")
        |    val all = Files::glob("$dir", "**/*.on")
        |    val sub = Files::glob("$dir", "sub/**/*.on")
        |    return all.toString() + " " + sub.toString()
        |  }
        |}
        |""".stripMargin,
      "FilesGlobGlobstar.on",
      Array()
    )
    assert(Shell.Success("[a.on, sub/b.on, sub/deep/c.on] [sub/b.on, sub/deep/c.on]") == result)
  }
}
