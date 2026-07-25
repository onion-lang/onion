package onion.compiler.tools

import onion.tools.Shell

/**
 * `Origin` is the runtime counterpart to the compiler's `onion.compiler.Location`:
 * where a value came from, in the text it was read out of.
 *
 * Onion had no such type — `provenance`, `sourceSpan` and `valueOrigin` matched nothing
 * repo-wide — so a parse failure could not say which line it happened on even when the
 * underlying parser knew (`Json.JsonParseException.position`, `Yaml.YamlParseException.line`
 * are both discarded by the generated code). Everything that reports a boundary failure
 * with a position is built on this.
 */
class OriginSpec extends AbstractShellSpec {

  describe("Origin") {
    it("carries source, line, column and span") {
      val result = shell.run(
        """import { onion.Origin; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val o = new Origin("access.log", 12, 5, 7)
          |    return o.source() + "|" + o.line() + "|" + o.column() + "|" + o.span()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("access.log|12|5|7") == result)
    }

    it("renders as file:line:column, the form every compiler prints") {
      val result = shell.run(
        """import { onion.Origin; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return Origin::at("access.log", 12, 5).describe()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("access.log:12:5") == result)
    }

    it("defaults the span to a single character via at()") {
      val result = shell.run(
        """import { onion.Origin; }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return Origin::at("x.on", 1, 1).span()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == result)
    }

    it("names a line with no column as just file:line") {
      // Json reports a character offset and Yaml a line; neither has a column, so an
      // Origin built from them must not invent one.
      val result = shell.run(
        """import { onion.Origin; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return Origin::atLine("data.json", 4).describe()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("data.json:4") == result)
    }

    it("compares by value, so defects can be deduplicated") {
      val result = shell.run(
        """import { onion.Origin; }
          |class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    return Origin::at("a.on", 1, 2) == Origin::at("a.on", 1, 2)
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(true) == result)
    }

    it("shifts to a line within an enclosing text, for per-line parsing") {
      // `S.lines` parses each line separately; each sub-parse reports positions relative
      // to its own line and has to be lifted back into the whole document.
      val result = shell.run(
        """import { onion.Origin; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return Origin::at("log.txt", 1, 3).onLine(40).describe()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("log.txt:40:3") == result)
    }
  }
}
