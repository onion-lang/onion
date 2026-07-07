package onion.compiler.tools

import onion.tools.Shell

/**
 * Two more condition/guard -> body narrowing sites, mirroring the if/while
 * narrowing of #294/#302/#303 (issue #304):
 *
 *  (A) a for-loop body is narrowed by the loop condition, exactly like a while
 *      body: `for ...; x != null && i < n; ... { x.length() }` narrows `x`.
 *  (B) a select case body is narrowed by its `when` guard, exactly like an
 *      `if`-then branch: `case s when s != null: s.length()` narrows `s`.
 *
 * Both are FLOW-SENSITIVE for a reassignable `var` and never leak past the
 * loop/select (soundness). Assertions are on Shell.Success/Failure (compile
 * outcome + return value), which are locale-independent — no localized text.
 */
class ForAndGuardNarrowingSpec extends AbstractShellSpec {

  // ---- (A) for-loop body: MUST WORK ----

  it("#304: the for-condition narrows a nullable parameter in the for-body") {
    // x is non-null while the loop runs; sum x.length() n times (2*3 = 6).
    assert(Shell.Success(6) == shell.run(
      "def f(x: String?, n: Int): Int {\n" +
        "  var total: Int = 0\n" +
        "  for var i: Int = 0; x != null && i < n; i = i + 1 { total = total + x.length() }\n" +
        "  return total }\n" +
        "def main(args: String[]): Int { return f(\"ab\", 3) }", "None", Array()))
  }

  // ---- (B) select guard: MUST WORK ----

  it("#304: a `when` guard narrows the case body (single binding)") {
    assert(Shell.Success(2) == shell.run(
      "def f(x: String?): Int {\n" +
        "  return select x {\n" +
        "    case s when s != null: s.length()\n" +
        "    else: -1\n" +
        "  } }\n" +
        "def main(args: String[]): Int { return f(\"hi\") }", "None", Array()))
  }

  it("#304: a `when` guard narrowing the scrutinee var itself (no binding used)") {
    // Guard tests the outer nullable var x directly; the body uses x, which is
    // narrowed to non-null by the guard. The false path returns -1.
    assert(Shell.Success(3) == shell.run(
      "def f(x: String?): Int {\n" +
        "  return select x {\n" +
        "    case s when x != null: x.length()\n" +
        "    else: -1\n" +
        "  } }\n" +
        "def main(args: String[]): Int { return f(\"abc\") }", "None", Array()))
  }

  it("#304: the guard's false path is NOT narrowed (runtime picks else)") {
    assert(Shell.Success(-1) == shell.run(
      "def f(x: String?): Int {\n" +
        "  return select x {\n" +
        "    case s when s != null: s.length()\n" +
        "    else: -1\n" +
        "  } }\n" +
        "def main(args: String[]): Int { return f(null) }", "None", Array()))
  }

  // ---- (A)/(B) soundness: MUST FAIL ----

  it("#304: the for-condition narrowing does not leak past the loop") {
    assert(Shell.Failure(-1) == shell.run(
      "def f(x: String?): Int {\n" +
        "  for var i: Int = 0; x != null && i < 1; i = i + 1 { }\n" +
        "  return x.length() }\n" +
        "def main(args: String[]): Int { return f(null) }", "None", Array()))
  }

  it("#304: a var reassigned in the for-body is not narrowed after the reassignment") {
    // The use before the body reassignment is narrowed; the use after `y = null`
    // must be rejected (flow-clearing at the reassignment point).
    assert(Shell.Failure(-1) == shell.run(
      "def f(x: String?): Int {\n" +
        "  var y: String? = x\n" +
        "  for var i: Int = 0; y != null && i < 1; i = i + 1 { y = null\n return y.length() }\n" +
        "  return -1 }\n" +
        "def main(args: String[]): Int { return f(\"hi\") }", "None", Array()))
  }

  it("#304: a var reassigned in the for-update is not narrowed in the body") {
    // y is narrowed in the condition but reassigned by the update each iteration,
    // so it is not reliably non-null at body entry — must be rejected.
    assert(Shell.Failure(-1) == shell.run(
      "def f(x: String?): Int {\n" +
        "  var y: String? = x\n" +
        "  for var i: Int = 0; y != null && i < 1; y = null { return y.length() }\n" +
        "  return -1 }\n" +
        "def main(args: String[]): Int { return f(\"hi\") }", "None", Array()))
  }

  it("#304: the select guard narrowing does not leak to the else body") {
    // The else body has no guard, so it must NOT see the first case's guard
    // narrowing: x may still be null there.
    assert(Shell.Failure(-1) == shell.run(
      "def f(x: String?): Int {\n" +
        "  return select x {\n" +
        "    case s when s != null: s.length()\n" +
        "    else: x.length()\n" +
        "  } }\n" +
        "def main(args: String[]): Int { return f(null) }", "None", Array()))
  }
}
