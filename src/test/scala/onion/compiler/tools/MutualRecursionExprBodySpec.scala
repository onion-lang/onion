package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression test for the @TailRecursive mutual recursion optimizer bug where
 * methods with expression-body `if`-expressions were not optimized.
 *
 * The type checker compiles `def f(n: Int): T = if cond { t1 } else { t2 }` to
 *   Return(Begin([StatementTerm(IfStatement(cond, assign(v,t1), assign(v,t2))), RefLocal(v)]))
 * rather than the statement-level
 *   IfStatement(cond, Return(t1), Return(t2))
 * form.  TailCallGraphAnalysis.findTailCalls and
 * transformMethodBodyForStateMachine only handled the latter, so the call
 * graph had no edges and MutualRecursionOptimization skipped the group
 * entirely, leaving the methods as plain recursive calls that overflow the
 * stack at large n.
 *
 * The fix adds a return-sinking normalization step that rewrites
 * Return(Begin(…)) into IfStatement(c, Return(t1), Return(t2)) before the
 * analysis, making the tail calls visible.
 */
class MutualRecursionExprBodySpec extends AbstractShellSpec {

  describe("@TailRecursive mutual recursion with expression-body if") {

    it("does not overflow the stack at depth 100000 (expression-body if form)") {
      val script =
        """
          |class ParityChecker {
          |private:
          |  @TailRecursive
          |  def isEven(n: Int): Boolean = if n == 0 { true } else { isOdd(n - 1) }
          |
          |  @TailRecursive
          |  def isOdd(n: Int): Boolean = if n == 0 { false } else { isEven(n - 1) }
          |
          |public:
          |  def checkEven(n: Int): Boolean = isEven(n)
          |
          |  static def main(args: String[]): Boolean = new ParityChecker().checkEven(100000)
          |}
          |""".stripMargin

      val result = shell.run(script, "MutualRecursionExprBody.on", Array())
      assert(result == Shell.Success(true), s"expected true, got $result")
    }

    it("produces correct results for small even and odd inputs") {
      val script =
        """
          |class P {
          |private:
          |  @TailRecursive
          |  def isEven(n: Int): Boolean = if n == 0 { true } else { isOdd(n - 1) }
          |
          |  @TailRecursive
          |  def isOdd(n: Int): Boolean = if n == 0 { false } else { isEven(n - 1) }
          |
          |public:
          |  static def main(args: String[]): String {
          |    val p: P = new P()
          |    val e0: Boolean = p.isEven(0)
          |    val o1: Boolean = p.isOdd(1)
          |    val e4: Boolean = p.isEven(4)
          |    val o5: Boolean = p.isOdd(5)
          |    return "" + e0 + o1 + e4 + o5
          |  }
          |}
          |""".stripMargin

      val result = shell.run(script, "MutualRecursionExprBodySmall.on", Array())
      assert(result == Shell.Success("truetruetruetrue"), s"unexpected $result")
    }
  }
}
