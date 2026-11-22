package onion.compiler.tools

import onion.tools.Shell

/**
 * The lens (issue #363): parse losslessly, focus an update with `edit`, reassemble
 * with `render`. The law on top of #362's L2: editing field X leaves every other byte
 * unchanged.
 */
class ConfigLensSpec extends AbstractShellSpec {

  private val decl =
    """record Server(host: String, port: Int, debug: Boolean) {
      |  shape cfg = config
      |}
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
          |      val out = lens.edit { v -> v.copy(debug = !v.debug()) }.render()
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
          |    return lens.edit { v -> v.copy(port = 2) }
          |               .edit { v -> v.copy(host = "b") }
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
          |    val out = lens.edit { v -> v.copy(port = 2) }.render()
          |    if out != "host = a\r\nport = 2\r\ndebug = false\r\n" { return 2 }
          |    return 0
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(0) == r, r.toString)
    }

    it("keeps a leading BOM through an edit and an unedited render (#3035)") {
      val r = shell.run(decl +
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val dir = java.nio.file.Files::createTempDirectory("lens-spec").toString()
          |    val p = dir + "/bom.conf"
          |    val bom = "\uFEFF"
          |    java.nio.file.Files::write(java.nio.file.Paths::get(p), (bom + "host = a\nport = 1\ndebug = false\n").getBytes("UTF-8"))
          |    val lens = file(p).readLossless(Server::cfg()).get()
          |    if lens.value().host() != "a" { return 1 }
          |    if lens.render() != bom + "host = a\nport = 1\ndebug = false\n" { return 2 }
          |    val out = lens.edit { v -> v.copy(port = 2) }.render()
          |    if out != bom + "host = a\nport = 2\ndebug = false\n" { return 3 }
          |    return 0
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(0) == r, r.toString)
    }

    it("hands a user-written lossless shape the text without the file's BOM, as read does (#3213)") {
      val r = shell.run(
        """record P(name: String)
          |class NoRes conforms Residue { public: def this {} }
          |class NameShape conforms Shape[P] {
          |public:
          |  def this {}
          |  def parse(text: String, origin: Origin): Outcome[P] = Outcome::ok(new P(text))
          |  def canPrint(): Boolean = true
          |  def print(v: P): String = v.name()
          |  def describe(): String = "name"
          |  def isLossless(): Boolean = true
          |  def parseLossless(text: String, origin: Origin): Outcome[Lossless[P]] = Outcome::ok(new Lossless[P](new P(text), new NoRes(), this))
          |  def printLossless(v: P, r: Residue): String = v.name()
          |}
          |def l1(): Boolean
          |  ensures result
          |{
          |  val s = new NameShape()
          |  return s.parse(s.print(new P("x"))).get() == new P("x")
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val dir = java.nio.file.Files::createTempDirectory("lens-spec").toString()
          |    val p = dir + "/name.txt"
          |    java.nio.file.Files::write(java.nio.file.Paths::get(p), ("\uFEFF" + "alice").getBytes("UTF-8"))
          |    val a = file(p).readLossless(new NameShape()).get().value().name()
          |    val b = file(p).read(new NameShape()).get().name()
          |    if a != "alice" { return 1 }
          |    if a != b { return 2 }
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
