package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import onion.tools.Shell

import java.io.StringReader

/**
 * Regression test for #1484: a `@TailRecursive` mutual-recursion group member that
 * declares an ordinary local `val` before its tail call (no `this` usage at all)
 * produced a bytecode `VerifyError` at class-load time instead of either compiling
 * correctly or being rejected at compile time.
 *
 * `MutualRecursionOptimization.rewriteParameterReferences` only redirects references
 * with `index < paramCount` to the merged state machine's loop-variable slots; a local
 * declared beyond the parameter list keeps its original slot index, which collides with
 * the loop/state/temp variable slots the merged method introduces at exactly that range.
 * `validateGroup` must now detect this and fall back to the ordinary (non-tail-optimized)
 * methods, reporting W0016, the same degraded-but-safe path used for the `this` case.
 */
class MutualRecursionLocalVarSpec extends AbstractShellSpec {

  private val script =
    """
      |record Box(v: Int)
      |
      |class TwoState {
      |private:
      |  @TailRecursive
      |  def stateA(n: Int, acc: Box): Box {
      |    if n <= 0 { return acc }
      |    val next: Box = new Box(acc.v() + 1)
      |    return stateB(n - 1, next)
      |  }
      |
      |  @TailRecursive
      |  def stateB(n: Int, acc: Box): Box {
      |    if n <= 0 { return acc }
      |    return stateA(n - 1, new Box(acc.v() + 2))
      |  }
      |
      |public:
      |  def run(n: Int): Box = stateA(n, new Box(0))
      |}
      |
      |static def main(args: String[]): Int {
      |  val t: TwoState = new TwoState()
      |  return t.run(10).v()
      |}
    """.stripMargin

  describe("@TailRecursive mutual recursion with a local variable before the tail call") {
    it("compiles and runs correctly instead of producing a VerifyError") {
      val result = shell.run(script, "MutualRecursionLocalVar.on", Array())
      // Without the fix, class loading fails with java.lang.VerifyError before this
      // assertion is even reached; with the fix, the group falls back to the ordinary
      // (non-tail-optimized) methods and computes the correct result.
      assert(result == Shell.Success(15))
    }

    it("reports W0016 for the group instead of silently degrading") {
      val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On)
      val compileResult = new OnionCompiler(config)
        .compileDetailed(Seq(new StreamInputSource(() => new StringReader(script), "W.on")))
      assert(!compileResult.hasErrors, s"expected a clean compile, got: ${compileResult.allErrors.map(_.message)}")
      val w16 = compileResult.diagnostics.warnings.filter(_.category.code == "W0016")
      assert(w16.length == 2, s"expected 2 W0016 (one per method), got: ${compileResult.diagnostics.warnings.map(_.message)}")
      assert(w16.forall(_.message.contains("local variable")))
    }
  }
}
