package onion.compiler.tools

import onion.tools.Shell

/**
 * A `var` that is only ever read/assigned by a closure nested *inside* another
 * closure (not directly by the closure immediately enclosing it) was never
 * marked as boxed: `CapturedVariableScanner.scan` only scans one closure level
 * deep and explicitly refuses to descend into a closure nested within the one
 * it is examining ("they will be handled separately" -- but nothing else ever
 * examines it). This is the transitive-capture sibling of issue #214, which
 * fixed the single-level (closure directly captures the outer var) case.
 *
 * Two symptoms of the same root cause:
 *  - Silently wrong results: each closure level got its own disconnected
 *    plain-field copy instead of sharing one heap-boxed cell, so a mutation
 *    made by the inner closure was invisible to the declaring scope.
 *  - A compiler crash ([I0000], inconsistent stackmap frames) when the value
 *    assigned from the innermost closure runs its own `try`/`catch`: codegen's
 *    unboxed-captured-variable assignment path pushes `this` before evaluating
 *    the value, and (like issue #745/#669 and friends) the JVM clears the
 *    operand stack when dispatching to the `catch` handler, discarding the
 *    pending `this` on the exceptional path and desyncing the merged stack
 *    shape from the normal path.
 */
class NestedClosureCapturedVariableSpec extends AbstractShellSpec {
  describe("a var captured only by a closure nested two levels deep") {
    it("propagates the innermost closure's mutation back to the declaring scope") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 0
          |    val outer: () -> Int = () -> {
          |      val inner: () -> Int = () -> {
          |        x = 7
          |        return x
          |      }
          |      return inner.call()
          |    }
          |    val ok: Int = outer.call()
          |    return ok + x
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      // If the inner closure's write is lost (unboxed, disconnected copy),
      // `x` in the declaring scope stays 0 and this totals 7, not 14.
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
          |      val inner: () -> Int = () -> {
          |        x = try { Integer::parseInt("7") } catch _: Exception { -1 }
          |        return x
          |      }
          |      return inner.call()
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
