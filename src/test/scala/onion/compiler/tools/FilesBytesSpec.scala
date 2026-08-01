package onion.compiler.tools

import onion.tools.Shell

/**
 * `Files::readBytes` and `Files::writeBytes` are implemented and documented
 * in docs/reference/stdlib.md right next to the other `Files` methods, but
 * unlike `readText`/`writeText`, never invoked by a `shell.run` test case
 * anywhere in the suite. Added a round-trip case.
 */
class FilesBytesSpec extends AbstractShellSpec {
  it("writes bytes to a file and reads them back") {
    val path = System.getProperty("java.io.tmpdir") + "/onion-files-bytes-test.bin"
    val result = shell.run(
      s"""
        |class Test {
        |public:
        |  static def main(args: String[]): String {
        |    val data: Byte[] = new Byte[3]
        |    data[0] = (72 as Byte)
        |    data[1] = (73 as Byte)
        |    data[2] = (33 as Byte)
        |    Files::writeBytes("$path", data)
        |    val read: Byte[] = Files::readBytes("$path")
        |    Files::delete("$path")
        |    val sb = new StringBuilder()
        |    foreach b: Byte in read {
        |      sb.append(b as Int)
        |      sb.append(",")
        |    }
        |    return sb.toString()
        |  }
        |}
        |""".stripMargin,
      "FilesBytesBasic.on",
      Array()
    )
    assert(Shell.Success("72,73,33,") == result)
  }
}
