package onion.compiler.tools

import onion.tools.Shell

/**
 * Integer literal parsing never throws an internal error (I0000). `Int.MIN` and
 * `Long.MIN` magnitudes parse (they used to crash the parser with a
 * NumberFormatException); a genuinely out-of-range literal is a clean error.
 */
class IntegerLiteralRangeSpec extends AbstractShellSpec {
  it("parses Integer.MIN_VALUE as -2147483648") {
    assert(Shell.Success(-2147483648) == shell.run(
      "def main(args: String[]): Int { return -2147483648 }", "None", Array()))
  }
  it("parses Long.MIN_VALUE") {
    assert(Shell.Success(java.lang.Long.MIN_VALUE) == shell.run(
      "def main(args: String[]): Long { return -9223372036854775808L }", "None", Array()))
  }
  it("parses Integer.MAX_VALUE") {
    assert(Shell.Success(2147483647) == shell.run(
      "def main(args: String[]): Int { return 2147483647 }", "None", Array()))
  }
  it("parses a full-width hex pattern") {
    assert(Shell.Success(-1) == shell.run(
      "def main(args: String[]): Int { return 0xFFFFFFFF }", "None", Array()))
  }
  it("reports a clean error for an out-of-range int literal (no crash)") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { val i: Int = 5000000000 }", "None", Array()))
  }
  it("reports a clean error for an out-of-range byte literal") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { val b: Byte = 1000B }", "None", Array()))
  }
}
