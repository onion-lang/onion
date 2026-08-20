package onion.compiler.tools

import onion.tools.Shell

/**
 * A `try`/`catch` expression assigned to a `var` that has been boxed because
 * some closure captures it used to crash the compiler with an `[I0000]`
 * internal error during bytecode verification: the JVM clears the operand
 * stack when it dispatches to an exception handler, so the box reference
 * already pushed before the `try` was silently discarded on the exceptional
 * path, desyncing the merged stack shape from the normal path. This is the
 * boxed-local-variable sibling of the field/array-assignment fix for the
 * same underlying issue (#745 and friends).
 *
 * `emitSetLocal` in `AsmCodeGeneration.scala` now spills an already-pushed
 * box reference into a local before evaluating a value that may run its own
 * `try`, and reloads it afterward.
 */
class TryInBoxedLocalAssignmentSpec extends AbstractShellSpec {
  describe("try/catch expression assigned to a closure-captured (boxed) local variable") {
    it("does not corrupt the box reference when assigned from inside the capturing closure") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var x: Int = 0
          |    val f: () -> Int = () -> {
          |      x = try { Integer::parseInt("7") } catch _: Exception { -1 }
          |      return x
          |    }
          |    val ok: Int = f.call()
          |    x = 0
          |    val g: () -> Int = () -> {
          |      x = try { Integer::parseInt("nope") } catch _: Exception { -1 }
          |      return x
          |    }
          |    val caught: Int = g.call()
          |    return ok + "|" + caught
          |  }
          |}
          |""".stripMargin,
        "TryInClosureCapturedSet.on",
        Array()
      )
      assert(Shell.Success("7|-1") == result)
    }

    it("does not corrupt the box reference when assigned from the declaring scope") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var x: Int = 0
          |    val f: () -> Int = () -> x
          |    x = try { Integer::parseInt("7") } catch _: Exception { -1 }
          |    val a: Int = f.call()
          |    x = try { Integer::parseInt("nope") } catch _: Exception { -1 }
          |    val b: Int = f.call()
          |    return a + "|" + b
          |  }
          |}
          |""".stripMargin,
        "TryInOuterBoxedSet.on",
        Array()
      )
      assert(Shell.Success("7|-1") == result)
    }
  }
}
