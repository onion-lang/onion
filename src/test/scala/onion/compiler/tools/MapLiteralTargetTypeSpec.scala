package onion.compiler.tools

import onion.tools.Shell

/**
 * A map literal target-types its key and value to the expected map type, like a
 * list literal: a nullable value type (`Map[String, String?]`) and a supertype key
 * or value (`Map[String, Shape]` with distinct record subtypes) are honored instead
 * of failing on the entries' widened join.
 */
class MapLiteralTargetTypeSpec extends AbstractShellSpec {
  private val shapes =
    "sealed interface Shape\nrecord Circle(r: Int) <: Shape\nrecord Square(s: Int) <: Shape\n"

  it("target-types mixed-subtype values to the expected value supertype") {
    assert(Shell.Success(2) == shell.run(
      shapes + "def main(args: String[]): Int { val m: Map[String, Shape] = [\"c\": new Circle(1), \"s\": new Square(2)]\n return m.size() }", "None", Array()))
  }
  it("target-types mixed-subtype keys to the expected key supertype") {
    assert(Shell.Success(2) == shell.run(
      shapes + "def main(args: String[]): Int { val m: Map[Shape, String] = [new Circle(1): \"c\", new Square(2): \"s\"]\n return m.size() }", "None", Array()))
  }
  it("adopts a nullable value type") {
    assert(Shell.Success("n") == shell.run(
      "def main(args: String[]): String { val m: Map[String, String?] = [\"a\": \"x\", \"b\": null]\n return m.get(\"b\") ?: \"n\" }", "None", Array()))
  }
  it("still infers a homogeneous map with no expected type") {
    assert(Shell.Success(3) == shell.run(
      "def main(args: String[]): Int { val m = [\"a\": 1, \"b\": 2]\n return m.get(\"a\") + m.get(\"b\") }", "None", Array()))
  }
  it("rejects a value that does not fit the expected value type") {
    assert(Shell.Failure(-1) == shell.run(
      "def main(args: String[]): void { val m: Map[String, Int] = [\"a\": \"x\"] }", "None", Array()))
  }
}
