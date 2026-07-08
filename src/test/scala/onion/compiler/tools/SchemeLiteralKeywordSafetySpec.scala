package onion.compiler.tools

import onion.tools.Shell

/**
 * Keyword-safety for user-definable scheme-prefixed literals. A reserved keyword
 * immediately followed (no whitespace) by a string — e.g. {@code return"x"} —
 * MUST lex/parse as the keyword followed by a string literal, NOT as a scheme
 * call {@code return("x")}. Only a genuine identifier prefix forms a scheme
 * literal. (This is the regression that reverted an earlier lexer-only attempt.)
 */
class SchemeLiteralKeywordSafetySpec extends AbstractShellSpec {
  describe("keyword + string with no space stays keyword + string") {
    it("return\"x\" (no space) returns the string") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return"hello"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("hello") == result)
    }

    it("throw\"x\" (no space) is a type error (String not Throwable), not method-not-found") {
      // If this lexed as throw("x") it would be a "method throw not found" error;
      // instead it is throw + string, which fails type-checking as a non-Throwable.
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): void {
          |    throw"x"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("an identifier that merely starts with a keyword IS a scheme literal") {
      // "returnValue" is not the keyword "return"; it is an ordinary identifier.
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def returnValue(s: String): String = "got:" + s
          |  static def main(args: String[]): String {
          |    return returnValue"ok"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("got:ok") == result)
    }

    it("identifier prefix WITH a space is not a scheme literal (parse error)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def sql(q: String): String = q
          |  static def main(args: String[]): String {
          |    return sql "x"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
