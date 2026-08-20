package onion.compiler.tools

import onion.tools.Shell

/**
 * `NestedClosureCapturedVariableSpec` locked in the *two*-levels-deep case for
 * a `var` relayed from an outer scope through an intermediate closure to the
 * closure that actually mutates it (issue #756). Both `CapturedVariableScanner`
 * (typing) and `ClosureCodegen.emitNewClosure`'s `adjustedFrame`-based boxed-ness
 * lookup (codegen) were written to recurse to arbitrary depth rather than special-
 * cased for exactly two levels -- but nothing exercises a *third* level, where
 * the variable is relayed through two intermediate closures before the innermost
 * one mutates it. This closes that coverage gap rather than a live bug: both
 * cases already pass.
 */
class ThreeLevelNestedClosureCapturedVariableSpec extends AbstractShellSpec {
  describe("a var captured only by a closure nested three levels deep") {
    it("propagates the innermost closure's mutation back to the declaring scope") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 0
          |    val outer: () -> Int = () -> {
          |      val middle: () -> Int = () -> {
          |        val inner: () -> Int = () -> {
          |          x = 7
          |          return x
          |        }
          |        return inner.call()
          |      }
          |      return middle.call()
          |    }
          |    val ok: Int = outer.call()
          |    return ok + x
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      // If the innermost closure's write is lost anywhere along the two-level
      // relay (unboxed, disconnected copy), `x` in the declaring scope stays
      // 0 and this totals 7, not 14.
      assert(Shell.Success(14) == result)
    }

    it("does not crash when the innermost closure's assigned value runs its own try/catch") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var x: Int = 0
          |    val outer: () -> Int = () -> {
          |      val middle: () -> Int = () -> {
          |        val inner: () -> Int = () -> {
          |          x = try { Integer::parseInt("7") } catch _: Exception { -1 }
          |          return x
          |        }
          |        return inner.call()
          |      }
          |      return middle.call()
          |    }
          |    val ok: Int = outer.call()
          |    return ok + "|" + x
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("7|7") == result)
    }
  }
}
