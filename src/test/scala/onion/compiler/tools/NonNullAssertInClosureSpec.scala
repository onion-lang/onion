package onion.compiler.tools

import onion.tools.Shell

/**
 * A `!!` (non-null assertion) inside a lambda body no longer crashes codegen. The
 * closure's captured-variable collector had no case for NonNullAssert and threw a
 * MatchError (surfacing as I0000). This also fixed type-class methods used inside a
 * lambda in a constrained function (they lower to `dict!!.method(...)`).
 */
class NonNullAssertInClosureSpec extends AbstractShellSpec {
  it("compiles a bare !! inside a lambda") {
    assert(Shell.Success(2) == shell.run(
      "def main(args: String[]): Int { val f = (s: String?) -> s!!.length()\n return f.call(\"hi\") }", "None", Array()))
  }
  it("compiles a captured nullable !! inside a lambda") {
    assert(Shell.Success(10) == shell.run(
      "def main(args: String[]): Int { val n: Int? = 5\n val f = () -> n!! * 2\n return f.call() }", "None", Array()))
  }
  it("compiles a trait method (dict!!) used inside a lambda in a constrained fn") {
    assert(Shell.Success(6) == shell.run(
      "trait Num[T] { def add(a: T, b: T): T }\ninstance Num[Integer] { def add(a: Integer, b: Integer): Integer = a + b }\ndef sumL[T: Num](xs: List[T], z: T): T = xs.fold(z) { acc, x => Num[T]::add(acc, x) }\ndef main(args: String[]): Int { return sumL([1,2,3], 0) }", "None", Array()))
  }
}
