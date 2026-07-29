package onion.compiler.tools

import onion.tools.Shell

/**
 * Lossless shapes and residue (issue #362). L1 (`parse(print(v)) == Ok(v)`) is every
 * printing shape's law; L2 (`print(parse(t)) == t`) is false in general and true here:
 * `shape name = config` parses a commented `key = value` document keeping everything it
 * did not consume — comments, blank lines, spacing, key order, unknown keys, and the
 * original spelling of every value — as a `Residue`, and `printLossless` reassembles it.
 */
class ConfigLosslessShapeSpec extends AbstractShellSpec {

  private val decl =
    """record Server(host: String, port: Int, debug: Boolean)
      |  shape cfg = config
      |""".stripMargin

  describe("L2: print(parse(t)) == t") {
    it("reproduces a messy document byte for byte, trailing newline included") {
      val r = shell.run(decl +
        """class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val text = "# production\nhost = example.com\n\nport   =   007\ndebug = false\n# keep me\nextra = untouched\n"
          |    val got = Server::cfg().parseLossless(text).get()
          |    return Server::cfg().printLossless(got.value(), got.residue()) == text
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(true) == r, r.toString)
    }

    it("reproduces a document with no trailing newline") {
      val r = shell.run(decl +
        """class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val text = "host = h\nport = 1\ndebug = true"
          |    val got = Server::cfg().parseLossless(text).get()
          |    return Server::cfg().printLossless(got.value(), got.residue()) == text
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(true) == r, r.toString)
    }

    it("holds over a corpus of spacing/order/comment variations") {
      val r = shell.run(decl +
        """class Test {
          |public:
          |  static def check(text: String): Boolean {
          |    val got = Server::cfg().parseLossless(text).get()
          |    return Server::cfg().printLossless(got.value(), got.residue()) == text
          |  }
          |  static def main(args: String[]): Int {
          |    val corpus = [
          |      "host=h\nport=1\ndebug=true\n",
          |      "debug = true\nhost = h\nport = 1\n",
          |      "  # indented comment\nhost = h\nport = 0007\ndebug = false\n\n\n",
          |      "host = spaces  in  value\nport = 1\ndebug = true\n# tail comment"
          |    ]
          |    var ok = 0
          |    foreach t: String in corpus {
          |      if check(t) { ok = ok + 1 }
          |    }
          |    return ok
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(4) == r, r.toString)
    }
  }

  describe("editing through the residue") {
    it("re-renders only the edited value; comments, spacing and other spellings survive") {
      val r = shell.run(decl +
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val text = "# prod\nhost = a.example\nport   =   007\ndebug = false\n"
          |    val got = Server::cfg().parseLossless(text).get()
          |    val edited = got.value().copy(host = "b.example")
          |    return Server::cfg().printLossless(edited, got.residue())
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("# prod\nhost = b.example\nport   =   007\ndebug = false\n") == r, r.toString)
    }

    it("keeps an unchanged component's original spelling (007 stays 007)") {
      val r = shell.run(decl +
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val text = "host = h\nport = 007\ndebug = false\n"
          |    val got = Server::cfg().parseLossless(text).get()
          |    // port's typed value is 7; unchanged, so its spelling must survive
          |    val edited = got.value().copy(debug = true)
          |    return Server::cfg().printLossless(edited, got.residue())
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("host = h\nport = 007\ndebug = true\n") == r, r.toString)
    }
  }

  describe("plain-Shape behavior stays honest") {
    it("satisfies L1 through the canonical printer") {
      val r = shell.run(decl +
        """class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val s = Server::cfg()
          |    val v = new Server("h", 8080, true)
          |    return s.parse(s.print(v)).get() == v
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(true) == r, r.toString)
    }

    it("reports a missing key, a bad value, a duplicate and a malformed line as positioned defects") {
      val r = shell.run(decl +
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val missing = Server::cfg().parse("host = h\ndebug = true\n")
          |    val bad = Server::cfg().parse("host = h\nport = many\ndebug = true\n")
          |    val dup = Server::cfg().parse("host = a\nhost = b\nport = 1\ndebug = true\n")
          |    val malformed = Server::cfg().parse("host = h\nwhat is this\nport = 1\ndebug = true\n")
          |    return "" + missing.defects().size + (missing.defects()[0] as Defect).path() + "/" +
          |           bad.defects().size + (bad.defects()[0] as Defect).origin().line() + "/" +
          |           dup.defects().size + "/" + malformed.defects().size
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("1port/12/1/1") == r, r.toString)
    }
  }

  describe("a value that the line-based format cannot represent") {
    it("print refuses rather than silently splitting a value containing a newline into a bogus extra line") {
      val r = shell.run(decl +
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v = new Server("evil\nport = 9999", 8080, true)
          |    try {
          |      Server::cfg().print(v)
          |      return "no exception"
          |    } catch e: IllegalArgumentException {
          |      return "refused"
          |    }
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("refused") == r, r.toString)
    }

    it("printLossless refuses the same way, instead of injecting an indistinguishable extra entry") {
      val r = shell.run(decl +
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val text = "host = h\nport = 8080\ndebug = true\n"
          |    val got = Server::cfg().parseLossless(text).get()
          |    val edited = got.value().copy(host = "evil\nport = 9999")
          |    try {
          |      Server::cfg().printLossless(edited, got.residue())
          |      return "no exception"
          |    } catch e: IllegalArgumentException {
          |      return "refused"
          |    }
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("refused") == r, r.toString)
    }
  }

  describe("losslessness is a claim, not a default") {
    it("a lossy shape refuses parseLossless instead of pretending") {
      val r = shell.run(
        """record Pt(x: Int, y: Int)
          |  shape text = re"(-?\d+),(-?\d+)"
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    if Pt::text().isLossless() { return "claims lossless?!" }
          |    try {
          |      Pt::text().parseLossless("1,2")
          |      return "no exception"
          |    } catch e: UnsupportedOperationException {
          |      return "refused"
          |    }
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("refused") == r, r.toString)
    }

    it("a foreign residue is rejected, never misrendered through") {
      val r = shell.run(
        """record A(host: String, port: Int, debug: Boolean)
          |  shape cfg = config
          |record B(name: String)
          |  shape cfg = config
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val ra = A::cfg().parseLossless("host = h\nport = 1\ndebug = true\n").get()
          |    try {
          |      B::cfg().printLossless(new B("x"), ra.residue())
          |      return "accepted foreign residue?!"
          |    } catch e: IllegalArgumentException {
          |      return "rejected"
          |    }
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("rejected") == r, r.toString)
    }
  }
}
