package onion.compiler.tools

import onion.tools.Shell

/**
 * A `var` local never reassigned in its enclosing body is effectively final, so
 * a null / is check smart-casts it (issue #273) — just like an unassigned
 * method parameter and the effectively-final foreach var (#253). Before, a `var`
 * local was always mutable, so `if t != null { t.length }` wrongly reported
 * E0000 (String? used where non-null expected) even though the version worded
 * as `val` compiled. A `var` that IS reassigned must stay mutable and must NOT
 * be narrowed (soundness).
 */
class VarLocalNullableSmartCastSpec extends AbstractShellSpec {
  it("smart-casts a never-reassigned var local after a null check (top-level)") {
    assert(Shell.Success(2) == shell.run(
      "def main(args: String[]): Int { var t: String? = \"yo\"\n if t != null { return t.length() }\n return -1 }", "None", Array()))
  }
  it("smart-casts a never-reassigned var local inside a method") {
    assert(Shell.Success(5) == shell.run(
      "class C { public: static def go(): Int { var t: String? = \"hello\"\n if t != null { return t.length() }\n return -1 } }\n def main(args: String[]): Int { return C::go() }", "None", Array()))
  }
  it("smart-casts a never-reassigned nullable-primitive var (numeric add, not concat)") {
    assert(Shell.Success(11) == shell.run(
      "def main(args: String[]): Int { var x: Int? = 10\n if x != null { return x + 1 }\n return -1 }", "None", Array()))
  }
  it("smart-casts a never-reassigned var local by an is-pattern") {
    assert(Shell.Success(4) == shell.run(
      "def main(args: String[]): Int { var o: Object = \"text\"\n if o is String { return (o as String).length() }\n return -1 }", "None", Array()))
  }
  it("keeps a var reassigned AFTER the check mutable (not narrowed)") {
    // t is reassigned inside the guarded block, so it must not be narrowed:
    // t.length() on String? must stay a compile error (E0000).
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): Int { var t: String? = \"yo\"\n if t != null { t = null\n return t.length() }\n return -1 }", "None", Array()))
  }
  it("keeps a var reassigned ELSEWHERE in the body mutable (not narrowed)") {
    // t is reassigned later in the method (outside the guard), so the whole-body
    // scan marks it mutable; the check must not narrow it.
    assert(Shell.Failure(-1) == shell.run(
      "class C { public: static def go(s: String?): Int { var t: String? = s\n if t != null { return t.length() }\n t = \"x\"\n return t.length() } }\n def main(args: String[]): Int { return C::go(\"ab\") }", "None", Array()))
  }
}
