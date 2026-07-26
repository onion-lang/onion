package onion.compiler.tools

import onion.tools.Shell

/**
 * The lens (issue #363): parse losslessly, focus an update with `edit`, reassemble
 * with `render`. The law on top of #362's L2: editing field X leaves every other byte
 * unchanged.
 */
class ConfigLensSpec extends AbstractShellSpec {

  private val decl =
    """record Server(host: String, port: Int, debug: Boolean)
      |  shape cfg = config
      |""".stripMargin

  describe("edit / render") {
    it("renders an unedited lens back to the original text") {
      val r = shell.run(decl +
        """class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val t = "# c\nhost = h\nport = 007\ndebug = true\n"
          |    val r = Server::cfg().parseLossless(t).get()
          |    return r.render() == t
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(true) == r, r.toString)
    }

    it("editing field X leaves every other byte unchanged, over a corpus") {
      val r = shell.run(decl +
        """class Test {
          |public:
          |  static def oneLineDiff(before: String, after: String): Boolean {
          |    val a = Strings::split(before, "\n")
          |    val b = Strings::split(after, "\n")
          |    if a.size != b.size { return false }
          |    var changed = 0
          |    var i = 0
          |    while i < a.size {
          |      if a[i] != b[i] { changed = changed + 1 }
          |      i = i + 1
          |    }
          |    return changed == 1
          |  }
          |  static def main(args: String[]): Int {
          |    val corpus = [
          |      "# c\nhost = a\nport = 007\ndebug = false\n",
          |      "port=1\nhost=x\ndebug=true\nextra = zzz\n",
          |      "host = a\n\n\n  # deep comment\nport   =   2\ndebug = false"
          |    ]
          |    var ok = 0
          |    foreach t: String in corpus {
          |      val lens = Server::cfg().parseLossless(t).get()
          |      val out = lens.edit { v => v.copy(debug = !v.debug()) }.render()
          |      if oneLineDiff(t, out) { ok = ok + 1 }
          |    }
          |    return ok
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(3) == r, r.toString)
    }

    it("chains edits; the last render reflects all of them") {
      val r = shell.run(decl +
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val t = "host = a\nport = 1\ndebug = false\n"
          |    val lens = Server::cfg().parseLossless(t).get()
          |    return lens.edit { v => v.copy(port = 2) }
          |               .edit { v => v.copy(host = "b") }
          |               .render()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("host = b\nport = 2\ndebug = false\n") == r, r.toString)
    }
  }

  describe("the file half: readLossless") {
    it("reads byte-faithfully — trailing newline and CRLF are part of the file") {
      val r = shell.run(decl +
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val dir = java.nio.file.Files::createTempDirectory("lens-spec").toString()
          |    val p = dir + "/crlf.conf"
          |    Files::writeText(p, "host = a\r\nport = 1\r\ndebug = false\r\n")
          |    val lens = file(p).readLossless(Server::cfg()).get()
          |    // Unedited render reproduces the CRLF file exactly.
          |    if lens.render() != "host = a\r\nport = 1\r\ndebug = false\r\n" { return 1 }
          |    // An edited line keeps its terminator style.
          |    val out = lens.edit { v => v.copy(port = 2) }.render()
          |    if out != "host = a\r\nport = 2\r\ndebug = false\r\n" { return 2 }
          |    return 0
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(0) == r, r.toString)
    }

    it("reports an unreadable file as a defect, not an exception") {
      val r = shell.run(decl +
        """class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    return file("/no/such/dir/app.conf").readLossless(Server::cfg()).isBad()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(true) == r, r.toString)
    }
  }
}
