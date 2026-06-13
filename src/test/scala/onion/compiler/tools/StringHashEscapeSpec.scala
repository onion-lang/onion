package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression tests for `\#{` escape in string literals — previously the lexer
 * could not parse a `\#` escape at all, causing "string literal not closed"
 * parse errors.
 */
class StringHashEscapeSpec extends AbstractShellSpec {
  describe("\\#{ escape in string literals") {
    it("produces a literal #{ without triggering interpolation") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "\#{ not interpolated }"
          |  }
          |}
          |""".stripMargin,
        "StringHashEscape.on",
        Array()
      )
      assert(Shell.Success("#{ not interpolated }") == result)
    }

    it("\\#{ can be mixed with real interpolation") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val name = "world"
          |    return "hello #{name} and \#{ escaped }"
          |  }
          |}
          |""".stripMargin,
        "StringHashEscapeMixed.on",
        Array()
      )
      assert(Shell.Success("hello world and #{ escaped }") == result)
    }

    it("multiple \\#{ escapes work in one string") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "\#{ a } and \#{ b }"
          |  }
          |}
          |""".stripMargin,
        "StringMultiHashEscape.on",
        Array()
      )
      assert(Shell.Success("#{ a } and #{ b }") == result)
    }

    it("normal string interpolation still works after fix") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x = 42
          |    return "value is #{x}"
          |  }
          |}
          |""".stripMargin,
        "StringInterpStillWorks.on",
        Array()
      )
      assert(Shell.Success("value is 42") == result)
    }
  }
}
