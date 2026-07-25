package onion.compiler.tools

import onion.tools.Shell

/**
 * A generic constructor infers its type arguments from the constructor
 * arguments (issue #305), the way a generic *method* call already does:
 * `new Box("hi")` binds T = String instead of being rejected as a raw type
 * (E0066). Explicit type arguments and expected-type (target-typed) diamonds
 * keep working, the inferred type is a real type (not a silently-widened
 * Object), and a bare generic type with nothing to infer from is still an
 * E0066 raw type.
 */
class GenericConstructorInferenceSpec extends AbstractShellSpec {
  private val box =
    "class Box[T] { val value: T\npublic: def this(v: T) { this.value = v }\n def get(): T = value }\n"
  private val pair =
    "class Pair[A, B] { val a: A\n val b: B\npublic: def this(a: A, b: B) { this.a = a; this.b = b }\n" +
      " def first(): A = a\n def second(): B = b }\n"

  it("infers a type argument from a constructor argument") {
    assert(Shell.Success("HI") == shell.run(
      box + "def main(args: String[]): String { return new Box(\"hi\").get().toUpperCase() }", "None", Array()))
  }

  it("infers each type argument of a multi-parameter generic class") {
    assert(Shell.Success("1x") == shell.run(
      pair + "def main(args: String[]): String { val p = new Pair(1, \"x\")\n return \"\" + p.first() + p.second() }",
      "None", Array()))
  }

  it("boxes a primitive argument into the inferred type argument") {
    assert(Shell.Success(43) == shell.run(
      box + "def main(args: String[]): Int { return new Box(42).get() + 1 }", "None", Array()))
  }

  it("infers a nested generic argument") {
    assert(Shell.Success("in") == shell.run(
      box + "def main(args: String[]): String { return new Box(new Box(\"in\")).get().get() }", "None", Array()))
  }

  it("infers a type argument the receiver's own methods then depend on") {
    // If T had silently widened to Object, size() on the element would not resolve.
    assert(Shell.Success(2) == shell.run(
      box + "def main(args: String[]): Int { return new Box([\"a\", \"b\"]).get().size() }", "None", Array()))
  }

  it("keeps the inferred type argument sound against a mismatched expected type") {
    assert(Shell.Failure(-1) == shell.run(
      box + "def main(args: String[]): void { val wrong: Box[Int] = new Box(\"str\") }", "None", Array()))
  }

  it("still rejects a bare generic type with no argument to infer from") {
    assert(Shell.Failure(-1) == shell.run(
      "class Empty[T] { public: def this {} }\ndef main(args: String[]): void { val e = new Empty() }",
      "None", Array()))
  }

  it("still honors an explicit type argument over inference") {
    assert(Shell.Success(2) == shell.run(
      box + "def main(args: String[]): Int { val b = new Box[java.lang.CharSequence](new java.lang.StringBuilder(\"hi\"))\n return b.get().length() }",
      "None", Array()))
  }

  it("still honors an expected-type diamond over argument inference") {
    assert(Shell.Success("hi") == shell.run(
      box + "def main(args: String[]): String { val b: Box[java.lang.CharSequence] = new Box(\"hi\")\n return b.get().toString() }",
      "None", Array()))
  }
}
