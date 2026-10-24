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
  it("rejects an int label that does not fit a Byte scrutinee") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { val b: Byte = 44B\n val r = select b { case 300: \"bad\"\n else: \"ok\" } }", "None", Array()))
  }
  it("rejects an int label that does not fit a Short scrutinee") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { val s: Short = 4464S\n val r = select s { case 70000: \"bad\"\n else: \"ok\" } }", "None", Array()))
  }
  it("rejects a negative int label for a Char scrutinee") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { val c: Char = 65\n val r = select c { case -1: \"bad\"\n else: \"ok\" } }", "None", Array()))
  }
  it("still accepts labels at the edges of the type") {
    assert(Shell.Success("edge") == shell.run(
      "def main(args: String[]): String { val b: Byte = -128B\n return select b { case 127: \"hi\"\n case -128: \"edge\"\n else: \"?\" } }", "None", Array()))
  }
  it("never matches a computed label that does not fit a Byte scrutinee") {
    assert(Shell.Success("other") == shell.run(
      "def main(args: String[]): String { val c: Byte = 44B\n return select c { case 0x12C - 0: \"BAD\"\n else: \"other\" } }", "None", Array()))
    assert(Shell.Success("other") == shell.run(
      "def main(args: String[]): String { val c: Byte = 44B\n return select c { case 150 + 150: \"BAD\"\n else: \"other\" } }", "None", Array()))
  }
  it("never matches a variable label that does not fit a Short or Char scrutinee") {
    assert(Shell.Success("other") == shell.run(
      "def main(args: String[]): String { val s: Short = 4464S\n val k = 70000\n return select s { case k: \"BAD\"\n else: \"other\" } }", "None", Array()))
    assert(Shell.Success("other") == shell.run(
      "def main(args: String[]): String { val c: Char = 65535\n val k = -1\n return select c { case k: \"BAD\"\n else: \"other\" } }", "None", Array()))
  }
  it("still matches a computed label that fits") {
    assert(Shell.Success("hit") == shell.run(
      "def main(args: String[]): String { val c: Byte = -2B\n val k = 2\n return select c { case 0 - k: \"hit\"\n else: \"other\" } }", "None", Array()))
  }
}
