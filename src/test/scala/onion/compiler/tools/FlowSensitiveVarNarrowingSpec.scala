package onion.compiler.tools

import onion.tools.Shell

/**
 * Flow-sensitive smart cast for reassignable `var` locals (issues #288 / #289).
 *
 * A `var` narrows in the then-branch of `if v != null` (and the else of
 * `if v == null`) as long as `v` is not reassigned in the condition or WITHIN
 * that branch — a reassignment AFTER the branch does not invalidate the
 * narrowing inside it (#288). For `while ((v = e) != null) { body }`, `v` is
 * non-null at the top of every iteration, so it narrows at the start of the body
 * until reassigned there (#289). A reassignment between the check and the use
 * (in the condition or before the use) keeps `v` non-narrowed (unsafe). The
 * whole-scope effectively-final narrowing of #273 must keep working.
 *
 * Assertions are on Shell.Success/Failure (compile outcome + return value),
 * which are locale-independent — no localized error text.
 */
class FlowSensitiveVarNarrowingSpec extends AbstractShellSpec {

  // ---- #288: reassignment AFTER the guarded branch does not invalidate it ----

  it("#288: narrows a var in the then-branch even though it is reassigned later") {
    // `s` is reassigned to null AFTER the if, but the use inside the branch is
    // provably safe. Previously E0041 (String? target); now it compiles to 2.
    assert(Shell.Success(2) == shell.run(
      "def main(args: String[]): Int { var s: String? = \"hi\"\n" +
        " var out: Int = -1\n" +
        " if s != null { out = s.length() }\n" +
        " s = null\n" +
        " return out }", "None", Array()))
  }

  it("#288: narrows a var in the else-branch of an == null check reassigned later") {
    assert(Shell.Success(3) == shell.run(
      "def main(args: String[]): Int { var s: String? = \"abc\"\n" +
        " var out: Int = -1\n" +
        " if s == null { out = -1 } else { out = s.length() }\n" +
        " s = null\n" +
        " return out }", "None", Array()))
  }

  // ---- #289: while ((v = e) != null) narrows v in the loop body ----

  it("#289: narrows the assigned var at the top of a while-assign-in-condition body") {
    // nextLine yields "aa","bbb","c" then null; sum of lengths is 6.
    assert(Shell.Success(6) == shell.run(
      "class Src { public: var i: Int\n var data: String[]\n" +
        " def this { this.i = 0\n this.data = new String[3]\n data[0] = \"aa\"\n data[1] = \"bbb\"\n data[2] = \"c\" }\n" +
        " def nextLine(): String? { if i < 3 { var r: String? = data[i]\n i = i + 1\n return r } else { return null } } }\n" +
        "def main(args: String[]): Int { var src: Src = new Src()\n" +
        " var line: String? = \"\"\n var total: Int = 0\n" +
        " while (line = src.nextLine()) != null { total = total + line.length() }\n" +
        " return total }", "None", Array()))
  }

  // ---- unsafe: reassignment BETWEEN the check and the use stays non-narrowed --

  it("#288: a var reassigned to null WITHIN the branch before the use stays rejected") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): Int { var s: String? = \"hi\"\n" +
        " if s != null { s = null\n return s.length() }\n" +
        " return -1 }", "None", Array()))
  }

  it("#289: a var reassigned to null in the loop body before the use stays rejected") {
    assert(Shell.Failure(-1) == shell.run(
      "def next(): String? { return \"x\" }\n" +
        "def main(args: String[]): Int { var line: String? = \"\"\n" +
        " while (line = next()) != null { line = null\n return line.length() }\n" +
        " return -1 }", "None", Array()))
  }

  it("an unchecked use of a nullable var is not narrowed (no guarding check)") {
    // No null check guards the use, so the var stays String? and the call is
    // rejected — a flow narrowing only ever comes from an actual check.
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): Int { var s: String? = \"hi\"\n" +
        " return s.length() }", "None", Array()))
  }

  it("a var narrowed in the then-branch is NOT narrowed after the branch") {
    // The narrowing is bounded to the guarded region; the later unchecked use of
    // s stays String? and is rejected even though an earlier branch narrowed it.
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): Int { var s: String? = \"hi\"\n" +
        " if s != null { return s.length() }\n" +
        " return s.length() }", "None", Array()))
  }

  // ---- #273 regression: a never-reassigned var still narrows whole-scope ------

  it("#273: a never-reassigned var still narrows after a null check") {
    assert(Shell.Success(2) == shell.run(
      "def main(args: String[]): Int { var s: String? = \"hi\"\n" +
        " if s != null { return s.length() }\n" +
        " return -1 }", "None", Array()))
  }
}
