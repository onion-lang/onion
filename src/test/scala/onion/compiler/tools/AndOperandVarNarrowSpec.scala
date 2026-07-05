package onion.compiler.tools

import onion.tools.Shell

/**
 * A reassignable `var` narrowed by the left operand of `&&` is non-null in the
 * right operand too — the common `while (p != null && p.method())` loop (issue
 * #294). A var used in `&&` without a preceding null check is still rejected.
 */
class AndOperandVarNarrowSpec extends AbstractShellSpec {
  it("narrows a reassignable var in the && right operand of a while") {
    assert(Shell.Success(3) == shell.run(
      "def main(args: String[]): Int { var p: String? = \"abc\"\n var n = 0\n while p != null && p.length() > n { n = n + 1\n if n >= 3 { p = null } }\n return n }",
      "None", Array()))
  }
  it("narrows a reassignable var in the && right operand of an if") {
    assert(Shell.Success(2) == shell.run(
      "def main(args: String[]): Int { var p: String? = \"hi\"\n if p != null && p.length() > 0 { return p.length() }\n return -1 }",
      "None", Array()))
  }
  it("still rejects a var used in && without a null check") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { var p: String? = \"hi\"\n var n = 0\n while n < 3 && p.length() > 0 { n = n + 1 } }",
      "None", Array()))
  }
}
