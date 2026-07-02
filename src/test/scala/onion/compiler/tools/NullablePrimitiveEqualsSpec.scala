package onion.compiler.tools

import onion.tools.Shell

/**
 * A nullable primitive compares by value against a plain primitive with `==`
 * (`Int? == Int`), symmetric with `String? == String`. It used to be rejected with
 * E0001 because one operand was primitive and the other was not.
 */
class NullablePrimitiveEqualsSpec extends AbstractShellSpec {
  it("compares a nullable primitive to a primitive") {
    assert(Shell.Success(true) == shell.run(
      "def main(args: String[]): Boolean { val n: Int? = 5\n return n == 5 }", "None", Array()))
  }
  it("is symmetric") {
    assert(Shell.Success(true) == shell.run(
      "def main(args: String[]): Boolean { val n: Int? = 5\n return 5 == n }", "None", Array()))
  }
  it("is false when the nullable side is null") {
    assert(Shell.Success(false) == shell.run(
      "def main(args: String[]): Boolean { val n: Int? = null\n return n == 3 }", "None", Array()))
  }
}
