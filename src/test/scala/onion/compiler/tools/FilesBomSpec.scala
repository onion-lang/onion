package onion.compiler.tools

import onion.tools.Shell

/** #2880: a UTF-8 BOM is an encoding marker, so text readers must not return it as text. */
class FilesBomSpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result = {
    val path = (System.getProperty("java.io.tmpdir") + "/onion-bom-2880.txt").replace('\\', '/')
    shell.run(
      s"""
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    Files::writeText("$path", "\\uFEFFhi\\nj\\n")
        |    $body
        |  }
        |}
        |""".stripMargin.replace("$p", path),
      "FilesBom.on",
      Array()
    )
  }
  private val p = (System.getProperty("java.io.tmpdir") + "/onion-bom-2880.txt").replace('\\', '/')

  it("readText drops a leading BOM") {
    assert(Shell.Success("104") == run(s"""return "" + (Files::readText("$p").charAt(0) as Int)"""))
  }
  it("readLines drops a BOM from the first line only") {
    assert(Shell.Success("hi,j") == run(s"""return Files::readLines("$p").join(",")"""))
  }
  it("file literal text() drops a leading BOM") {
    assert(Shell.Success("104") == run(s"""return "" + (file("$p").text().charAt(0) as Int)"""))
  }
}
