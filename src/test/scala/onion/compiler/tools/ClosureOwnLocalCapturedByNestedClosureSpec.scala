package onion.compiler.tools

import onion.tools.Shell

/**
 * A `var` declared *inside* a closure's own body (not relayed from an outer
 * scope -- that's the case #756/`NestedClosureCapturedVariableSpec` fixed)
 * that is captured and mutated by a closure nested inside it was never
 * marked as boxed at codegen time: `ClosureCodegen.generateClosureMethod`
 * builds its `ClosureLocalVarContext` with only `.withParameters(...)`, never
 * `.withBoxedVariables(frame)` -- unlike the top-level method path in
 * `AsmCodeGeneration.codeMethod`, which always calls both. Typing correctly
 * determines the variable needs boxing (`TypedAST.NewClosure.frame` carries
 * that info), but codegen never consults it for a closure's own frame.
 *
 * Two symptoms of the same root cause, matching the family of bugs fixed for
 * the relayed-capture case:
 *  - Silently wrong results: the inner closure's mutation never reaches the
 *    outer closure's own copy of the local.
 *  - A compiler crash ([I0000], inconsistent stackmap frames) when the value
 *    assigned from the inner closure runs its own `try`/`catch`.
 */
class ClosureOwnLocalCapturedByNestedClosureSpec extends AbstractShellSpec {
  describe("a var declared inside a closure and captured only by a closure nested inside it") {
    it("propagates the inner closure's mutation back to the declaring closure") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var y: Int = 0
          |    val outer: () -> Int = () -> {
          |      var localVar: Int = 1
          |      val inner: () -> Int = () -> {
          |        localVar = 42
          |        return localVar
          |      }
          |      inner.call()
          |      y = localVar
          |      return y
          |    }
          |    outer.call()
          |    return y
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      // If the inner closure's write is lost (unboxed, disconnected copy),
      // `y` stays 1 instead of picking up the inner closure's write of 42.
      assert(Shell.Success(42) == result)
    }

    it("does not crash when the inner closure's assigned value runs its own try/catch") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var y: Int = 0
          |    val outer: () -> Int = () -> {
          |      var localVar: Int = 1
          |      val inner: () -> Int = () -> {
          |        localVar = try { Integer::parseInt("42") } catch _: Exception { -1 }
          |        return localVar
          |      }
          |      inner.call()
          |      y = localVar
          |      return y
          |    }
          |    outer.call()
          |    return "" + y
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("42") == result)
    }
  }
}
