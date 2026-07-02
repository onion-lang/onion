package onion.compiler.tools

import onion.tools.Shell

/**
 * Type classes stage 1: `[T: Numeric]` context-bound syntax parses on methods and
 * classes (and composes with `[T extends B]`). The constraint is not yet enforced
 * — that is a later typing step — so a constrained parameter currently behaves
 * like an unconstrained one.
 */
class TypeClassConstraintParsingSpec extends AbstractShellSpec {
  private val numeric = "trait Numeric[T] { def zero(): T }\ninstance Numeric[Integer] { def zero(): Integer = 0 }\n"
  describe("[T: C] constraint syntax") {
    it("parses on a generic method") {
      assert(Shell.Success("got 5") == shell.run(
        numeric + "def describe[T: Numeric](x: T): String { return \"got \" + x }\ndef main(args: String[]): String { return describe(5) }", "None", Array()))
    }
    it("parses a multi-parameter list with a mix of constrained/unconstrained") {
      assert(Shell.Success("1x") == shell.run(
        numeric + "def multi[T: Numeric, U](a: T, b: U): String { return \"\" + a + b }\ndef main(args: String[]): String { return multi(1, \"x\") }", "None", Array()))
    }
    it("parses on a generic class") {
      assert(Shell.Success(7) == shell.run(
        numeric + "class Box[T: Numeric] { val v: T\npublic: def this(x: T) { v = x }\n def get(): T = v }\ndef main(args: String[]): Int { return new Box[Integer](7).get() }", "None", Array()))
    }
    it("composes with an [T extends B] upper bound") {
      assert(Shell.Success(3) == shell.run(
        numeric + "def both[T extends Comparable[T]: Numeric](x: T): T { return x }\ndef main(args: String[]): Int { return both(3) }", "None", Array()))
    }
  }
}
