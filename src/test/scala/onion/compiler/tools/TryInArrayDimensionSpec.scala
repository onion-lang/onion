package onion.compiler.tools

import onion.tools.Shell

/**
 * A `try`/`catch` expression used as a later dimension of a multi-dimensional
 * array creation (`new Int[dim1][dim2]`) crashed the compiler with an
 * `[I0000]` internal error during bytecode verification: `visitNewArray`
 * pushed every dimension unconditionally before the `multianewarray`
 * instruction, and the JVM clears the operand stack when it dispatches to an
 * exception handler, so an earlier dimension already pushed was silently
 * discarded on the exceptional path, desyncing the merged stack shape from
 * the normal path. This is the array-creation sibling of the array-index,
 * array-assignment, and `new` (constructor-argument) fixes for the same
 * underlying issue (#745, #752, and friends).
 *
 * `AsmCodeGenerationVisitor.visitNewArray` now evaluates every dimension into
 * a plain local first (mirroring `visitNewObject`) whenever any dimension may
 * run its own `try`, and only then reloads them for `multianewarray`.
 */
class TryInArrayDimensionSpec extends AbstractShellSpec {
  describe("try/catch expression as a multi-dimensional array-creation dimension") {
    it("does not corrupt earlier dimensions across a caught later dimension") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val arr: Int[][] = new Int[3][try {
          |      Integer::parseInt("nope")
          |    } catch _: Exception {
          |      2
          |    }]
          |    return arr.length + "," + arr[0].length
          |  }
          |}
          |""".stripMargin,
        "TryInArrayDimensionCaught.on",
        Array()
      )
      assert(Shell.Success("3,2") == result)
    }

    it("does not corrupt earlier dimensions on the non-throwing path") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val arr: Int[][] = new Int[3][try {
          |      Integer::parseInt("4")
          |    } catch _: Exception {
          |      -1
          |    }]
          |    return arr.length + "," + arr[0].length
          |  }
          |}
          |""".stripMargin,
        "TryInArrayDimensionOk.on",
        Array()
      )
      assert(Shell.Success("3,4") == result)
    }

    it("preserves left-to-right evaluation order across dimensions") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static var log: String = ""
          |
          |  static def note(tag: String, value: Int): Int {
          |    Test::log = Test::log + tag
          |    return value
          |  }
          |
          |  static def boom(): Int {
          |    throw new Exception("boom")
          |  }
          |
          |  static def main(args: String[]): String {
          |    val arr: Int[][] = new Int[Test::note("D", 2)][try { Test::note("T", 0); Test::boom() } catch _: Exception { 5 }]
          |    return Test::log + ":" + arr.length + "," + arr[0].length
          |  }
          |}
          |""".stripMargin,
        "TryInArrayDimensionOrder.on",
        Array()
      )
      assert(Shell.Success("DT:2,5") == result)
    }
  }
}
