package onion.compiler.tools

import onion.tools.Shell

/**
 * The right operand of `*`, `/`, `%` may be a unary-prefixed expression, so
 * `7 * -3`, `7 / -3`, and `7 % -3` parse (they used to be syntax errors; only the
 * additive operators accepted a unary-minus operand). Left-associativity and
 * precedence are unchanged.
 */
class UnaryMultiplicativeOperandSpec extends AbstractShellSpec {
  it("parses a negative right operand of %") {
    assert(Shell.Success(1) == shell.run(
      "def main(args: String[]): Int { return 7 % -3 }", "None", Array()))
  }
  it("parses a negative right operand of * and /") {
    assert(Shell.Success(-21) == shell.run(
      "def main(args: String[]): Int { return 7 * -3 }", "None", Array()))
    assert(Shell.Success(-2) == shell.run(
      "def main(args: String[]): Int { return 7 / -3 }", "None", Array()))
  }
  it("keeps * / % left-associative") {
    assert(Shell.Success(5) == shell.run(
      "def main(args: String[]): Int { return 100 / 10 / 2 }", "None", Array()))
    assert(Shell.Success(2) == shell.run(
      "def main(args: String[]): Int { return 20 % 7 % 4 }", "None", Array()))
  }
  it("keeps multiplicative-over-additive precedence") {
    assert(Shell.Success(14) == shell.run(
      "def main(args: String[]): Int { return 2 + 3 * 4 }", "None", Array()))
  }
}
