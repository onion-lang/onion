package onion.compiler.tools

import onion.tools.Shell

/**
 * Unary minus keeps a Byte/Short operand's type, so its result must stay in that
 * type's range. `-128B` is `-(128B)`, and `128B` is already the byte -128; negating it
 * used to leave the int 128 in a Byte slot, which printed 128 and crashed boxing (#2297).
 */
class SubIntNegationSpec extends AbstractShellSpec {
  private def runInt(body: String): Shell.Result =
    shell.run(s"def main(args: String[]): Int { $body }", "None", Array())

  private def runString(body: String): Shell.Result =
    shell.run(s"def main(args: String[]): String { $body }", "None", Array())

  it("-128B widens to -128") {
    assert(Shell.Success(-128) == runInt("val h: Byte = -128B; val i: Int = h; return i"))
  }
  it("-128B boxes without crashing") {
    assert(Shell.Success("-128") == runString("val h: Byte = -128B; val o: Object = h; return o.toString()"))
  }
  it("-32768S widens to -32768") {
    assert(Shell.Success(-32768) == runInt("val s: Short = -32768S; val i: Int = s; return i"))
  }
  it("-32768S boxes without crashing") {
    assert(Shell.Success("-32768") == runString("val s: Short = -32768S; val o: Object = s; return o.toString()"))
  }
  it("negating a runtime Byte -128 wraps to -128, as the Byte type requires") {
    assert(Shell.Success(-128) == runInt("val b: Byte = ((0 - 128) as Byte); val n: Byte = -b; val i: Int = n; return i"))
  }
  it("negating a runtime Short -32768 wraps to -32768") {
    assert(Shell.Success(-32768) == runInt("val s: Short = ((0 - 32768) as Short); val n: Short = -s; val i: Int = n; return i"))
  }
  it("negating a Char gives the same Int as Java (-97 for 'a'), as before #2297") {
    assert(Shell.Success(-97) == runInt("val c: Char = 'a'; val i: Int = -c; return i"))
  }
  it("ordinary Byte and Short negation is unchanged") {
    assert(Shell.Success(-5) == runInt("val b: Byte = 5B; val n: Byte = -b; val i: Int = n; return i"))
    assert(Shell.Success(7) == runInt("val s: Short = -7S; val i: Int = s; return -i"))
    assert(Shell.Success(-127) == runInt("val b: Byte = -127B; val i: Int = b; return i"))
  }
}
