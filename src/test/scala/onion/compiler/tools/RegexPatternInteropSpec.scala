package onion.compiler.tools

import onion.tools.Shell

/**
 * A re"..." literal (a java.util.regex.Pattern) works with the Regex:: helpers
 * via Pattern overloads, so the shape-first regex literals interop with the
 * regex library.
 */
class RegexPatternInteropSpec extends AbstractShellSpec {
  describe("Regex:: Pattern overloads") {
    it("matches with a re literal") {
      assert(Shell.Success(true) == shell.run(
        "def main(args: String[]): Boolean { return Regex::matches(\"hello123\", re\"[a-z]+\\d+\") }", "None", Array()))
    }
    it("findFirst with a re literal") {
      assert(Shell.Success("42") == shell.run(
        "def main(args: String[]): String { return Regex::findFirst(\"xx 42 yy\", re\"\\d+\") }", "None", Array()))
    }
    it("replace and split with re literals") {
      assert(Shell.Success("a#b#") == shell.run(
        "def main(args: String[]): String { return Regex::replace(\"a1b2\", re\"\\d\", \"#\") }", "None", Array()))
      assert(Shell.Success(3) == shell.run(
        "def main(args: String[]): Int { return Regex::split(\"a,b,c\", re\",\").length }", "None", Array()))
    }
    it("still accepts String patterns") {
      assert(Shell.Success(true) == shell.run(
        "def main(args: String[]): Boolean { return Regex::matches(\"abc\", \"[a-z]+\") }", "None", Array()))
    }
  }
}
