package onion.compiler.tools

import onion.tools.Shell

/**
 * Type classes: a `[T: Trait]` context bound is now resolved during typing. A
 * constraint that names an unknown trait is rejected (previously silently
 * accepted); a real trait constraint still compiles. The resolved traits are
 * stored for the eventual dictionary-parameter derivation.
 */
class TypeClassConstraintResolutionSpec extends AbstractShellSpec {
  describe("[T: Trait] constraint resolution") {
    it("rejects a constraint naming an unknown trait") {
      assert(Shell.Failure(-1) == shell.run(
        "def f[T: Unknown](x: T): T = x\ndef main(args: String[]): Int { return f(3) }", "None", Array()))
    }
    it("accepts a real trait constraint") {
      assert(Shell.Success(3) == shell.run(
        "trait Numeric[T] { def z(): T }\ninstance Numeric[Integer] { def z(): Integer = 0 }\ndef f[T: Numeric](x: T): T = x\ndef main(args: String[]): Int { return f(3) }", "None", Array()))
    }
    it("accepts multiple constraints and an upper bound together") {
      assert(Shell.Success("ok") == shell.run(
        "trait A[X] { def a(x: X): Int }\ntrait B[X] { def b(x: X): Int }\ninstance A[String] { def a(x: String): Int = 1 }\ninstance B[String] { def b(x: String): Int = 2 }\ndef f[T extends Object: A + B](x: T): Int = 0\ndef main(args: String[]): String { f(\"z\")\n return \"ok\" }", "None", Array()))
    }
  }
}
