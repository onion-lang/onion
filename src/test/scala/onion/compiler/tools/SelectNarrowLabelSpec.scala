package onion.compiler.tools

import onion.tools.Shell

/**
 * A `select` over a byte/short/char scrutinee accepts int case labels (compared by
 * value, like Java's switch), narrowing the label to the scrutinee type. Char
 * scrutinees still accept char literals; incompatible label types are rejected.
 */
class SelectNarrowLabelSpec extends AbstractShellSpec {
  it("matches int labels against a Char scrutinee") {
    assert(Shell.Success("B") == shell.run(
      "def main(args: String[]): String { val c: Char = 66\n return select c { case 65: \"A\"\n case 66: \"B\"\n else: \"?\" } }", "None", Array()))
  }
  it("matches int labels against a Byte scrutinee") {
    assert(Shell.Success("two") == shell.run(
      "def main(args: String[]): String { val b: Byte = 2\n return select b { case 1: \"one\"\n case 2: \"two\"\n else: \"?\" } }", "None", Array()))
  }
  it("matches int labels against a Short scrutinee") {
    assert(Shell.Success("hundred") == shell.run(
      "def main(args: String[]): String { val s: Short = 100\n return select s { case 100: \"hundred\"\n else: \"?\" } }", "None", Array()))
  }
  it("still accepts char literals for a Char scrutinee") {
    assert(Shell.Success("b") == shell.run(
      "def main(args: String[]): String { val c: Char = 66\n return select c { case 'A': \"a\"\n case 'B': \"b\"\n else: \"?\" } }", "None", Array()))
  }
  it("still rejects an incompatible label type") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { val b: Byte = 2\n val r = select b { case \"x\": \"bad\" } }", "None", Array()))
  }
}
