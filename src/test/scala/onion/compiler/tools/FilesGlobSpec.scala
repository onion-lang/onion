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

  it("a **/ inside a brace alternative matches zero directories; a leading ./ is ignored (#2906)") {
    val dir = (System.getProperty("java.io.tmpdir") + "/onion-files-glob-2906").replace('\\', '/')
    val result = shell.run(
      s"""
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    Files::mkdirs("$dir/sub")
        |    Files::writeText("$dir/a.on", "")
        |    Files::writeText("$dir/README.md", "")
        |    Files::writeText("$dir/sub/b.on", "")
        |    val braces = Files::glob("$dir", "{**/*.on,*.md}")
        |    val dotted = Files::glob("$dir", "./**/*.on")
        |    return braces.toString() + " " + dotted.toString()
        |  }
        |}
        |""".stripMargin,
      "FilesGlobBrace.on",
      Array()
    )
    assert(Shell.Success("[README.md, a.on, sub/b.on] [a.on, sub/b.on]") == result)
  }

  it("a bracket class with a comma or brace inside a brace group is not split (#3123)") {
    val dir = (System.getProperty("java.io.tmpdir") + "/onion-files-glob-3123").replace('\\', '/')
    val result = shell.run(
      s"""
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    Files::mkdirs("$dir/sub")
        |    Files::writeText("$dir/a.on", "")
        |    Files::writeText("$dir/b.on", "")
        |    Files::writeText("$dir/x.md", "")
        |    Files::writeText("$dir/{q", "")
        |    Files::writeText("$dir/sub/c.on", "")
        |    val mixed = Files::glob("$dir", "{[a,b].on,*.md}")
        |    val brace = Files::glob("$dir", "[{,}]*")
        |    return mixed.toString() + " " + brace.toString()
        |  }
        |}
        |""".stripMargin,
      "FilesGlobBracketComma.on",
      Array()
    )
    assert(Shell.Success("[a.on, b.on, x.md] [{q]") == result)
  }
}
