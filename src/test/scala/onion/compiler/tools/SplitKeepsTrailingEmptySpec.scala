package onion.compiler.tools

import onion.tools.Shell

// #2941: splitting "a,b,," must give four fields, as in Python and JS.
class SplitKeepsTrailingEmptySpec extends AbstractShellSpec {
  private def run(expr: String): Shell.Result = shell.run(
    s"""
       |import { onion.Regex; onion.Strings; }
       |class Test {
       |public:
       |  static def main(args: String[]): String {
       |    return "" + ($expr)
       |  }
       |}
       |""".stripMargin,
    "None",
    Array()
  )

  describe("split keeps trailing empty fields") {
    it("Strings::split") {
      assert(Shell.Success("4") == run("""Strings::split("a,b,,", ",").size"""))
    }
    it("Strings::split reads a row with an empty last column") {
      assert(Shell.Success("") == run("""Strings::split("alice,30,", ",")[2]"""))
    }
    it("Strings::splitRegex") {
      assert(Shell.Success("4") == run("""Strings::splitRegex("a;b;;", ";").size"""))
    }
    it("Regex::split with a String pattern") {
      assert(Shell.Success("4") == run("""Regex::split("x;y;;", ";").size"""))
    }
    it("Regex::split with a Pattern") {
      assert(Shell.Success("4") == run("""Regex::split("x;y;;", re";").size"""))
    }
    it("an input with no trailing delimiter is unchanged") {
      assert(Shell.Success("[a, b, c]") == run("""Strings::split("a,b,c", ",")"""))
    }
    it("an empty input gives one empty field") {
      assert(Shell.Success("1") == run("""Strings::split("", ",").size"""))
    }
  }
}
