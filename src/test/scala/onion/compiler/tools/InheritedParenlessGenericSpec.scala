package onion.compiler.tools

import onion.tools.Shell

/**
 * A parenless member inherited from a generic ancestor sees the ancestor's
 * type parameter through the subclass's extends clause (#2033), as the
 * explicit-parens call `ib.get()` already does.
 */
class InheritedParenlessGenericSpec extends AbstractShellSpec {
  private val box =
    "class Box[T] {\n  var value: T\npublic:\n  def this(v: T) { value = v }\n  def get: T = value\n}\n" +
    "class IntBox(v: Int) extends Box[Int](v) {\n}\n"

  it("substitutes T for an inherited parenless method called from outside") {
    assert(Shell.Success(21) == shell.run(
      box + "class Main { public: static def main(args: String[]): Int { val ib: IntBox = new IntBox(21)\n val x: Int = ib.get\n return x } }",
      "None", Array()))
  }

  it("substitutes T for an inherited field read from outside") {
    assert(Shell.Success(21) == shell.run(
      "class Box[T] {\npublic:\n  var value: T\n  def this(v: T) { value = v }\n}\n" +
      "class IntBox(v: Int) extends Box[Int](v) {\n}\n" +
      "class Main { public: static def main(args: String[]): Int { val ib: IntBox = new IntBox(21)\n val x: Int = ib.value\n return x } }",
      "None", Array()))
  }

  it("substitutes T for a parenless self-call inside the subclass") {
    assert(Shell.Success(22) == shell.run(
      "class Box[T] {\n  var value: T\npublic:\n  def this(v: T) { value = v }\n  def get: T = value\n}\n" +
      "class IntBox(v: Int) extends Box[Int](v) {\npublic:\n  def next: Int = get + 1\n}\n" +
      "class Main { public: static def main(args: String[]): Int { return new IntBox(21).next } }",
      "None", Array()))
  }
}
