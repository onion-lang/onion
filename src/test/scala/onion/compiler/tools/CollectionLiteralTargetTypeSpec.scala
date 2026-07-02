package onion.compiler.tools

import onion.tools.Shell

/**
 * A collection literal adopts the expected element type when every element fits
 * it, so `val xs: List[String?] = ["a", null]` builds a List[String?] instead of a
 * List[String] that then fails to assign. (Nullable-primitive lists like
 * List[Int?] and `as` casts between differing type arguments are still open.)
 */
class CollectionLiteralTargetTypeSpec extends AbstractShellSpec {
  it("builds a List[String?] from a literal containing null") {
    assert(Shell.Success("n") == shell.run(
      "def main(args: String[]): String { val xs: List[String?] = [\"a\", null]\n return xs[1] ?: \"n\" }", "None", Array()))
  }
  it("builds a List[String?] from all-non-null elements") {
    assert(Shell.Success("a") == shell.run(
      "def main(args: String[]): String { val xs: List[String?] = [\"a\", \"b\"]\n return xs[0] ?: \"n\" }", "None", Array()))
  }
  it("still infers List[String] with no expected type") {
    assert(Shell.Success(2) == shell.run(
      "def main(args: String[]): Int { val xs = [\"a\", \"b\"]\n return xs.size() }", "None", Array()))
  }
  it("rejects an element that does not fit the expected element type") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { val xs: List[String?] = [\"a\", 1] }", "None", Array()))
  }
}
