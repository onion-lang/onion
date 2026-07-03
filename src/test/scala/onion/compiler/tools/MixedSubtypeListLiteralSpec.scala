package onion.compiler.tools

import onion.tools.Shell

/**
 * A list literal of distinct subtypes adopts the expected element type when every
 * element fits it, so `val es: List[Event] = [new Click(..), new Key(..)]` builds a
 * List[Event] instead of a List of the elements' widened join (Object), which then
 * failed to assign. Extends the reference-type target-typing to the subtype case.
 */
class MixedSubtypeListLiteralSpec extends AbstractShellSpec {
  private val decls =
    "sealed interface Event\nrecord Click(x: Int, y: Int) <: Event\nrecord Key(code: Int) <: Event\n"

  it("builds a List[Event] from mixed record subtypes") {
    assert(Shell.Success(3) == shell.run(
      decls + "def main(args: String[]): Int { val es: List[Event] = [new Click(1, 2), new Key(3), new Click(4, 5)]\n return es.size() }", "None", Array()))
  }
  it("still infers a homogeneous element type with no expected type") {
    assert(Shell.Success(3) == shell.run(
      "def main(args: String[]): Int { val xs = [1, 2, 3]\n return xs.size() }", "None", Array()))
  }
  it("still adopts a nullable reference element type") {
    assert(Shell.Success("n") == shell.run(
      "def main(args: String[]): String { val xs: List[String?] = [\"a\", null]\n return xs.get(1) ?: \"n\" }", "None", Array()))
  }
  it("rejects an element that does not fit the expected element type") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { val xs: List[String?] = [\"a\", 1] }", "None", Array()))
  }
}
