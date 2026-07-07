package onion.compiler.tools

import onion.tools.Shell

/**
 * A `while` body is narrowed by its condition exactly like an `if`-then branch
 * (issue #303). The body runs only when the condition holds, so `while x != null`
 * narrows `x` to non-null at the top of the body.
 *
 * The narrowing is FLOW-SENSITIVE for a reassignable `var`: the value is non-null
 * at the top of the body (uses before a reassignment are narrowed), and a
 * reassignment inside the body clears the narrowing from that point on (uses
 * after the reassignment are not narrowed). This is exactly what makes the
 * idiomatic linked-list / pointer-advance loop work — the narrowed `cur` is
 * reassigned by `cur = cur.next`.
 *
 * Assertions are on Shell.Success/Failure (compile outcome + return value), which
 * are locale-independent — no localized error text.
 */
class WhileConditionNarrowingSpec extends AbstractShellSpec {

  // ---- MUST WORK ----

  it("#303: linked-list pointer-advance loop narrows the reassigned var") {
    // cur is a var reassigned by `cur = cur.next` in the body; the use before
    // the reassignment (cur.value) must be narrowed to non-null. Walk a->b->c
    // and sum the value lengths (1+1+1 = 3).
    assert(Shell.Success(3) == shell.run(
      "class N {\n" +
        "public:\n" +
        "  var value: String\n" +
        "  var next: N?\n" +
        "  def this(v: String) { this.value = v\n this.next = null }\n" +
        "}\n" +
        "def main(args: String[]): Int {\n" +
        "  var a: N = new N(\"x\")\n" +
        "  var b: N = new N(\"y\")\n" +
        "  var c: N = new N(\"z\")\n" +
        "  a.next = b\n b.next = c\n" +
        "  var cur: N? = a\n" +
        "  var total: Int = 0\n" +
        "  while cur != null {\n" +
        "    total = total + cur.value.length()\n" +
        "    cur = cur.next\n" +
        "  }\n" +
        "  return total }", "None", Array()))
  }

  it("#303: a never-reassigned parameter narrows in the while body") {
    // Loop guard also bounds the iteration; sum x.length() n times.
    assert(Shell.Success(6) == shell.run(
      "def f(x: String?, n: Int): Int {\n" +
        "  var i: Int = 0\n" +
        "  var total: Int = 0\n" +
        "  while x != null && i < n { total = total + x.length()\n i = i + 1 }\n" +
        "  return total }\n" +
        "def main(args: String[]): Int { return f(\"ab\", 3) }", "None", Array()))
  }

  // ---- MUST FAIL (soundness) ----

  it("#303: a use AFTER a reassignment in the body is NOT narrowed") {
    // cur is reassigned first, so the subsequent cur.value use may be null and
    // must be rejected (flow-clearing at the reassignment point).
    assert(Shell.Failure(-1) == shell.run(
      "class N {\n" +
        "public:\n" +
        "  var value: String\n" +
        "  var next: N?\n" +
        "  def this(v: String) { this.value = v\n this.next = null }\n" +
        "}\n" +
        "def main(args: String[]): Int {\n" +
        "  var cur: N? = new N(\"x\")\n" +
        "  while cur != null {\n" +
        "    cur = cur.next\n" +
        "    IO::println(cur.value)\n" +
        "  }\n" +
        "  return -1 }", "None", Array()))
  }

  it("#303: the narrowing does not leak past the loop") {
    assert(Shell.Failure(-1) == shell.run(
      "def f(x: String?): Int {\n" +
        "  while x != null { }\n" +
        "  return x.length() }\n" +
        "def main(args: String[]): Int { return f(null) }", "None", Array()))
  }

  it("#303: a var set to null in the body before the use stays rejected") {
    assert(Shell.Failure(-1) == shell.run(
      "def f(x: String?): Int {\n" +
        "  var y: String? = x\n" +
        "  while y != null { y = null\n return y.length() }\n" +
        "  return -1 }\n" +
        "def main(args: String[]): Int { return f(\"hi\") }", "None", Array()))
  }
}
