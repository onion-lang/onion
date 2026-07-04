package onion.compiler.tools

import onion.tools.Shell

/**
 * A collection literal target-typed to a nullable-WRAPPER element (`List[Integer?]`,
 * `Map[String, Integer?]`) is now honored, matching the primitive `List[Int?]` form
 * (issue #255). Previously the adopt-expected check used a strict
 * `isAssignable(Integer?, Int)` which is false for a primitive element, so the
 * literal fell back to `List[Int]` and failed to assign. The check now also accepts
 * an element that fits the expected type's non-null boxed inner (a fresh literal is
 * safely typed at the wider nullable element type).
 */
class NullableWrapperCollectionLiteralSpec extends AbstractShellSpec {
  it("target-types a list literal to List[Integer?]") {
    assert(Shell.Success(3) == shell.run(
      "def main(args: String[]): Int { val xs: List[Integer?] = [1, null, 3]\n return xs.size() }", "None", Array()))
  }
  it("target-types a single-element list to List[Integer?]") {
    assert(Shell.Success(1) == shell.run(
      "def main(args: String[]): Int { val xs: List[Integer?] = [1]\n return xs.size() }", "None", Array()))
  }
  it("target-types a map value to Map[String, Integer?]") {
    assert(Shell.Success(2) == shell.run(
      "def main(args: String[]): Int { val m: Map[String, Integer?] = [\"a\": 1, \"b\": null]\n return m.size() }", "None", Array()))
  }
  it("still target-types the primitive List[Int?] form") {
    assert(Shell.Success(3) == shell.run(
      "def main(args: String[]): Int { val xs: List[Int?] = [1, null, 3]\n return xs.size() }", "None", Array()))
  }
  it("still builds a non-null List[Integer]") {
    assert(Shell.Success(2) == shell.run(
      "def main(args: String[]): Int { val xs: List[Integer] = [1, 2]\n return xs.size() }", "None", Array()))
  }
}
