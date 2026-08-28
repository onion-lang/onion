package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression tests for the parser bug where `else` on the line after a
 * single-line `if` body failed with a syntax error.
 *
 * When a block body fits on one line (`if cond { body }`), the IN_STATEMENT
 * lexer mode entered by expression_element() pre-tokenizes the newline after
 * `}` as an <EOL> token before leaveSection() fires. That stale EOL persists
 * in the token buffer even after the state reverts to DEFAULT, so a bare
 * LOOKAHEAD(2) "else" would see <EOL> as token 1 and skip the else branch,
 * treating the `else` keyword as a syntax error.
 *
 * Fixed by changing the lookahead in if_expression() to use elseFollows()
 * (which peeks past any buffered EOL tokens) and consuming them with eols()
 * before matching "else".
 */
class IfElseNewlineContinuationSpec extends AbstractShellSpec {

  describe("if/else with single-line body and else on next line") {
    it("parses a two-branch if/else with compact bodies on separate lines") {
      val result = shell.run(
        """
          |def abs(n: Int): Int = {
          |  if n < 0 { n * -1 }
          |  else { n }
          |}
          |def main(): void { println(abs(-5)) }
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(null) == result, s"expected successful run but got: $result")
    }

    it("parses a three-branch if/else-if/else chain with compact bodies") {
      val result = shell.run(
        """
          |def clamp(n: Int, lo: Int, hi: Int): Int = {
          |  if n < lo { lo }
          |  else if n > hi { hi }
          |  else { n }
          |}
          |def main(): void {
          |  println(clamp(5, 0, 10))
          |  println(clamp(-5, 0, 10))
          |  println(clamp(15, 0, 10))
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(null) == result, s"expected successful run but got: $result")
    }

    it("parses else-if chain inside a method body") {
      val result = shell.run(
        """
          |class Classifier {
          |public:
          |  static def classify(n: Int): String {
          |    if n < 0 { return "negative" }
          |    else if n == 0 { return "zero" }
          |    else { return "positive" }
          |  }
          |  static def main(args: String[]): Int {
          |    IO::println(classify(-1))
          |    IO::println(classify(0))
          |    IO::println(classify(1))
          |    return 0
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(0) == result, s"expected 0 but got: $result")
    }

    it("parses else after single-line if body at top level") {
      val result = shell.run(
        """
          |def sign(n: Int): Int = {
          |  if n > 0 { 1 }
          |  else if n < 0 { -1 }
          |  else { 0 }
          |}
          |def main(): void {
          |  println(sign(42))
          |  println(sign(-7))
          |  println(sign(0))
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(null) == result, s"expected successful run but got: $result")
    }

    it("existing multi-line if/else still works") {
      val result = shell.run(
        """
          |def abs(n: Int): Int = {
          |  if n < 0 {
          |    n * -1
          |  } else {
          |    n
          |  }
          |}
          |def main(): void { println(abs(-3)) }
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(null) == result, s"expected successful run but got: $result")
    }

    it("existing same-line if/else still works") {
      val result = shell.run(
        """
          |def abs(n: Int): Int = {
          |  if n < 0 { n * -1 } else { n }
          |}
          |def main(): void { println(abs(-8)) }
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(null) == result, s"expected successful run but got: $result")
    }
  }
}
