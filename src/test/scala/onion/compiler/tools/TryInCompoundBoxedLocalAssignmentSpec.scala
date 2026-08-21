package onion.compiler.tools

import onion.tools.Shell

/**
 * A `try`/`catch` expression on the right-hand side of a compound assignment
 * (`+=`) targeting a `var` that is boxed because a closure captures it.
 * Compound assignment desugars `target op= value` to `target = target op
 * value` (`SimpleExpressionTypingSupport.typeBinaryAssignmentSimple`), so the
 * `try` ends up nested inside a `BinaryTerm` rather than directly as the
 * assigned value -- the boxed-local sibling of
 * `TryInCompoundFieldArrayAssignmentSpec`, which locked in the same shape for
 * field/array targets but left the boxed-local path (`emitSetLocal` in
 * `AsmCodeGeneration.scala`) uncovered. This closes that coverage gap rather
 * than a live bug: `TermContainsTry.contains` recurses generically through a
 * term's children, so it already detects the nested `try` and spills the box
 * reference correctly in both cases below.
 */
class TryInCompoundBoxedLocalAssignmentSpec extends AbstractShellSpec {
  describe("try/catch expression as the RHS of a compound assignment to a closure-captured (boxed) local variable") {
    it("does not corrupt the box reference when compound-assigned from inside the capturing closure") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def risky(x: Int): Int {
          |    if x > 0 { return x }
          |    throw new Exception("boom")
          |  }
          |
          |  static def main(args: String[]): String {
          |    var x: Int = 10
          |    val f: () -> Int = () -> {
          |      x += try { Test::risky(5) } catch e: Exception { 0 }
          |      return x
          |    }
          |    val a: Int = f.call()
          |    x += try { Test::risky(-1) } catch e: Exception { 1 }
          |    return a + "|" + x
          |  }
          |}
          |""".stripMargin,
        "TryInCompoundBoxedLocalAssignmentInner.on",
        Array()
      )
      assert(Shell.Success("15|16") == result)
    }

    it("does not corrupt the box reference when compound-assigned from the declaring scope") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def risky(x: Int): Int {
          |    if x > 0 { return x }
          |    throw new Exception("boom")
          |  }
          |
          |  static def main(args: String[]): String {
          |    var x: Int = 10
          |    val f: () -> Int = () -> x
          |    x += try { Test::risky(5) } catch e: Exception { 0 }
          |    val a: Int = f.call()
          |    x += try { Test::risky(-1) } catch e: Exception { 1 }
          |    val b: Int = f.call()
          |    return a + "|" + b
          |  }
          |}
          |""".stripMargin,
        "TryInCompoundBoxedLocalAssignmentOuter.on",
        Array()
      )
      assert(Shell.Success("15|16") == result)
    }
  }
}
