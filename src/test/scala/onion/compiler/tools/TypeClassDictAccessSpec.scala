package onion.compiler.tools

import onion.tools.Shell

/**
 * Type classes stage 1: `Trait[TypeArgs]::method(args)` dictionary access. For
 * ground type arguments it resolves to the lowered instance class and works
 * end-to-end; the dictionary-passing form for an abstract type parameter is a
 * later stage (it currently reports a clean "type not found" error).
 */
class TypeClassDictAccessSpec extends AbstractShellSpec {
  private val numeric =
    "trait Numeric[T] { def zero(): T\n def plus(a: T, b: T): T }\n" +
    "instance Numeric[Integer] { def zero(): Integer = 0\n def plus(a: Integer, b: Integer): Integer { return a + b } }\n"
  describe("Trait[T]::method dictionary access (ground types)") {
    it("calls a nullary trait method") {
      assert(Shell.Success(0) == shell.run(
        numeric + "def main(args: String[]): Int { return Numeric[Integer]::zero() }", "None", Array()))
    }
    it("calls a binary trait method") {
      assert(Shell.Success(7) == shell.run(
        numeric + "def main(args: String[]): Int { return Numeric[Integer]::plus(3, 4) }", "None", Array()))
    }
    it("nests dictionary accesses") {
      assert(Shell.Success(9) == shell.run(
        numeric + "def main(args: String[]): Int { return Numeric[Integer]::plus(Numeric[Integer]::zero(), 9) }", "None", Array()))
    }
    it("computes a real sum through ground dictionary access") {
      assert(Shell.Success(10) == shell.run(
        numeric + "def sumL(xs: List[Integer]): Integer { var acc: Integer = Numeric[Integer]::zero()\n foreach x: Integer in xs { acc = Numeric[Integer]::plus(acc, x) }\n return acc }\ndef main(args: String[]): Int { return sumL([1,2,3,4]) }", "None", Array()))
    }
    it("does not disturb ordinary static calls") {
      assert(Shell.Success("42") == shell.run(
        "def main(args: String[]): String { return Long::toString(42L) }", "None", Array()))
    }
  }
}
