package onion.compiler.tools

import onion.tools.Shell

/**
 * A foreach loop variable never reassigned in the body is effectively final, so
 * a null / is check smart-casts it (issue #253, aspect 1) — just like an
 * unassigned method parameter. Before, the loop variable was always mutable, so
 * it was never narrowed: `x + 1` on a `List[Int?]` element silently
 * string-concatenated ("101") and `2 * x` reported E0001. A loop variable that
 * IS reassigned in the body stays mutable and is not narrowed.
 */
class ForeachNullableSmartCastSpec extends AbstractShellSpec {
  it("smart-casts a nullable-primitive foreach var after a null check (sum)") {
    assert(Shell.Success(40) == shell.run(
      "def main(args: String[]): Int { val xs: List[Int?] = [10, null, 30]\n var sum = 0\n foreach x: Int? in xs { if x != null { sum += x } }\n return sum }", "None", Array()))
  }
  it("does numeric addition (not string concat) on a narrowed foreach var") {
    assert(Shell.Success(11) == shell.run(
      "def main(args: String[]): Int { val xs: List[Int?] = [10]\n var r = 0\n foreach x: Int? in xs { if x != null { r = x + 1 } }\n return r }", "None", Array()))
  }
  it("allows multiplication with the narrowed foreach var as the right operand") {
    assert(Shell.Success(20) == shell.run(
      "def main(args: String[]): Int { val xs: List[Int?] = [10]\n var r = 0\n foreach x: Int? in xs { if x != null { r = 2 * x } }\n return r }", "None", Array()))
  }
  it("smart-casts a foreach var by an is-pattern") {
    assert(Shell.Success(2) == shell.run(
      "def main(args: String[]): Int { val xs: List[Object] = [\"hi\"]\n var r = 0\n foreach o: Object in xs { if o is String { r = (o as String).length() } }\n return r }", "None", Array()))
  }
  it("keeps a reassigned foreach loop variable mutable") {
    assert(Shell.Success(60) == shell.run(
      "def main(args: String[]): Int { var s = 0\n foreach x: Int in [1, 2, 3] { x = x * 10\n s += x }\n return s }", "None", Array()))
  }
}
