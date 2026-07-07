package onion.compiler.tools

import onion.tools.Shell

/**
 * Null narrowing through `||`, the De Morgan dual of the `&&` narrowing (#294).
 *
 * In `a || b` the right operand `b` is only evaluated when `a` is false, so if
 * `a` is `x == null` then `x != null` holds while typing `b`. Likewise after a
 * `if x == null || cond { return }` guard, the fall-through implies `x != null`
 * (issue #302). Soundness: `x != null || ...` must NOT narrow x in the right
 * operand (there x could still be null).
 */
class OrOperandNullNarrowSpec extends AbstractShellSpec {
  // (a) right operand of || is narrowed by a `x == null` left operand
  it("narrows a null-checked value in the || right operand") {
    assert(Shell.Success(false) == shell.run(
      "def f(x: String?): Boolean = x == null || x.length() == 0\n" +
      "def main(args: String[]): Boolean { return f(\"hi\") }",
      "None", Array()))
  }

  // (b) the ||-guard fall-through narrows x to non-null
  it("narrows x to non-null after a ||-guard that returns") {
    assert(Shell.Success(2) == shell.run(
      "def g(x: String?): Int {\n" +
      "  if x == null || x.length() == 0 { return -1 }\n" +
      "  return x.length()\n" +
      "}\n" +
      "def main(args: String[]): Int { return g(\"hi\") }",
      "None", Array()))
  }

  // (b') a three-operand ||-guard still narrows the null-checked value
  it("narrows through a chained ||-guard with an extra condition") {
    assert(Shell.Success(3) == shell.run(
      "def g(x: String?, flag: Boolean): Int {\n" +
      "  if x == null || flag || x.length() == 0 { return -1 }\n" +
      "  return x.length()\n" +
      "}\n" +
      "def main(args: String[]): Int { return g(\"abc\", false) }",
      "None", Array()))
  }

  // (c) && narrowing (#294) is unaffected
  it("still narrows in the && right operand") {
    assert(Shell.Success(true) == shell.run(
      "def f(x: String?): Boolean = x != null && x.length() == 0\n" +
      "def main(args: String[]): Boolean { return f(\"\") }",
      "None", Array()))
  }

  // (d) SOUNDNESS: `x != null || ...` must NOT narrow x (x may be null there)
  it("does NOT narrow x in the || right operand when the left is x != null") {
    assert(Shell.Failure(-1) == shell.run(
      "def h(x: String?): Boolean = x != null || x.length() == 0\n" +
      "def main(args: String[]): Boolean { return h(\"hi\") }",
      "None", Array()))
  }

  // (d') SOUNDNESS: a `(x == null && cond)` left operand of || does NOT narrow
  // (its falseness does not imply x is non-null)
  it("does NOT narrow x when the || left operand is (x == null && cond)") {
    assert(Shell.Failure(-1) == shell.run(
      "def g(x: String?, flag: Boolean): Boolean = (x == null && flag) || x.length() == 0\n" +
      "def main(args: String[]): Boolean { return g(\"a\", false) }",
      "None", Array()))
  }

  // (e) plain `if x != null { }` and early-return still work
  it("still narrows in a plain if x != null block") {
    assert(Shell.Success(3) == shell.run(
      "def f(x: String?): Int {\n" +
      "  if x != null { return x.length() }\n" +
      "  return -1\n" +
      "}\n" +
      "def main(args: String[]): Int { return f(\"abc\") }",
      "None", Array()))
  }
}
