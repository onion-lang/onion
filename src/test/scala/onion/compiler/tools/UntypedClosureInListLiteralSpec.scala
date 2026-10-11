package onion.compiler.tools

import onion.tools.Shell

/**
 * A closure with no expected type context at all (one sitting inside a list
 * literal that is itself an argument to a generic extension method, e.g.
 * `.concat(...)`) infers its own `FunctionN[...]` type from its argument and
 * body types (`ClosureTyping.inferFunctionType`). That inference used a
 * primitive result/argument type (e.g. `BasicType.INT`) directly as a generic
 * type argument instead of boxing it first, producing a `Function0[Int]` that
 * looks identical to a declared `Function0[Int]` when printed but is actually
 * a different (unboxed) type -- generics are invariant, so the two never
 * compared equal and the call was rejected with a message that named the same
 * type on both sides of "expected ... but ... is used".
 */
class UntypedClosureInListLiteralSpec extends AbstractShellSpec {

  private def runInt(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Integer {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "UntypedClosureInListLiteral.on", Array()))
  }

  describe("an untyped closure with no expected type, nested in a list literal") {
    it("a zero-arg closure's inferred result still unifies through an extension method's type parameter") {
      runInt(
        "val thunks: List[Function0[Integer]] = [() -> 1]\n" +
        "val more: List[Function0[Integer]] = thunks.concat([() -> 2])\n" +
        "return more.size()",
        Shell.Success(2))
    }

    it("a typed-parameter closure's inferred result also unifies through concat") {
      runInt(
        "val fs: List[Function1[Integer, Integer]] = [(x: Integer) -> x + 1]\n" +
        "val more: List[Function1[Integer, Integer]] = fs.concat([(x: Integer) -> x + 2])\n" +
        "return more.size()",
        Shell.Success(2))
    }
  }
}
