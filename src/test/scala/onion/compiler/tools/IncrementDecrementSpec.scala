package onion.compiler.tools

import onion.tools.Shell

/**
 * `++`/`--` work on every numeric lvalue. A Long/Double/Float local or field used
 * to crash codegen (I0000) because the literal 1 was an int; the increment now
 * matches the operand type. Array elements (`a[i]++`) are also supported and
 * evaluate the index once.
 */
class IncrementDecrementSpec extends AbstractShellSpec {
  it("increments a Long local (previously crashed)") {
    assert(Shell.Success(7L) == shell.run(
      "def main(args: String[]): Long { var x = 5L\n x++\n x++\n return x }", "None", Array()))
  }
  it("decrements a Double local (previously crashed)") {
    assert(Shell.Success(4.0) == shell.run(
      "def main(args: String[]): Double { var d = 5.0\n d--\n return d }", "None", Array()))
  }
  it("increments a Long field (previously crashed)") {
    assert(Shell.Success(11L) == shell.run(
      "class C { public: var big: Long = 10L\n def this{} }\ndef main(args: String[]): Long { val c = new C()\n c.big++\n return c.big }", "None", Array()))
  }
  it("increments an array element") {
    assert(Shell.Success(6) == shell.run(
      "def main(args: String[]): Int { val a = new Int[1]\n a[0] = 5\n a[0]++\n return a[0] }", "None", Array()))
  }
  it("post-increment yields the old element value") {
    assert(Shell.Success("10,11") == shell.run(
      "def main(args: String[]): String { val a = new Int[1]\n a[0] = 10\n val old = a[0]++\n return old + \",\" + a[0] }", "None", Array()))
  }
  it("evaluates a side-effecting array index only once") {
    assert(Shell.Success("1,6") == shell.run(
      "class Log { public: static var n: Int = 0 }\ndef idx(): Int { Log::n = Log::n + 1\n return 0 }\ndef main(args: String[]): String { val a = new Int[1]\n a[0] = 5\n a[idx()]++\n return Log::n + \",\" + a[0] }", "None", Array()))
  }
  it("still increments an Int local") {
    assert(Shell.Success(6) == shell.run(
      "def main(args: String[]): Int { var x = 5\n x++\n return x }", "None", Array()))
  }
}
