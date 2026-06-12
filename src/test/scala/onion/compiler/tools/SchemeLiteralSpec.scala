package onion.compiler.tools

import onion.tools.Shell

/**
 * Scheme-prefixed raw string literals: re"..." / file"..." / http"..." desugar
 * to the unqualified calls re(...) / file(...) / http(...), resolved through
 * the default static imports (onion.Resources). The body is raw — backslashes
 * pass through verbatim, so regex patterns need no double escaping.
 */
class SchemeLiteralSpec extends AbstractShellSpec {
  describe("scheme-prefixed literals") {
    it("re\"...\" yields a compiled Pattern with raw backslashes") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val p = re"\d+-\d+"
          |    return p.matcher("12-34").matches()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(true) == result)
    }

    it("file\"...\" yields a FileResource equal to the function form") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = file"/tmp/does-not-exist-xyz"
          |    val b = file("/tmp/does-not-exist-xyz")
          |    return a.path() + ":" + b.path() + ":" + a.exists()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("/tmp/does-not-exist-xyz:/tmp/does-not-exist-xyz:false") == result)
    }

    it("http\"...\" yields an HttpResource carrying the URL") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return http"https://example.com/x".url()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("https://example.com/x") == result)
    }

    it("identifiers named re/file/http still work") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val file = "name"
          |    val re = 42
          |    return file.length() + re
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(46) == result)
    }
  }

  describe("Csv stdlib") {
    it("parses RFC4180 quoting and maps header rows") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val rows = Csv::parse("a,b\n1,\"x,\"\"y\"\"\"\n")
          |    val recs = Csv::parseWithHeader("name,amount\nA,10\n")
          |    return rows[1][1] + "|" + recs[0].get("amount")
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("x,\"y\"|10") == result)
    }
  }
}
