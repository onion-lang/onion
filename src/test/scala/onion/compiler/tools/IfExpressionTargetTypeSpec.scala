package onion.compiler.tools

import onion.tools.Shell

/**
 * An if-expression target-types to the expected type: when its branches are
 * distinct subtypes (or a value and null) whose join is wider than the expected
 * type but each branch fits it, the expression adopts the expected type instead of
 * producing an unassignable join.
 */
class IfExpressionTargetTypeSpec extends AbstractShellSpec {
  private val decls =
    "sealed interface Event\nrecord Click(x: Int) <: Event\nrecord Key(c: Int) <: Event\n"

  it("adopts the expected supertype for mixed-subtype branches") {
    assert(Shell.Success("click1") == shell.run(
      decls + "def main(args: String[]): String { val e: Event = if true { new Click(1) } else { new Key(2) }\n return select e { case Click(x): \"click\" + x\n case Key(c): \"key\" + c } }", "None", Array()))
  }
  it("adopts a nullable expected type for a value/null pair") {
    assert(Shell.Success("n") == shell.run(
      "def main(args: String[]): String { val x: String? = if false { \"a\" } else { null }\n return x ?: \"n\" }", "None", Array()))
  }
  it("still infers a common type with no expected type") {
    assert(Shell.Success(11) == shell.run(
      "def main(args: String[]): Int { val x = if true { 1 } else { 2 }\n return x + 10 }", "None", Array()))
  }
  it("still works as a method body expression") {
    assert(Shell.Success(1) == shell.run(
      "def f(b: Boolean): Int = if b { 1 } else { 2 }\ndef main(args: String[]): Int { return f(true) }", "None", Array()))
  }
}
