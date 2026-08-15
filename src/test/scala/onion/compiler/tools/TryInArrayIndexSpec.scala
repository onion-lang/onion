package onion.compiler.tools

import onion.tools.Shell

/**
 * A `try`/`catch` expression used as the index of an array-read (`arr[...]`)
 * crashed the compiler with an `[I0000]` internal error during bytecode
 * verification, the read-side sibling of issue #745's array-assignment bug:
 * `visitRefArray` pushes the array reference unconditionally before
 * evaluating the index, and the JVM clears the operand stack when it
 * dispatches to an exception handler, so that already-pushed reference was
 * silently discarded on the exceptional path, desyncing the merged stack
 * shape from the normal path.
 *
 * `AsmCodeGenerationVisitor.visitRefArray` now spills the array reference
 * into a local before evaluating an index that may run its own `try`, and
 * reloads it afterward.
 */
class TryInArrayIndexSpec extends AbstractShellSpec {
  describe("try/catch expression as an array-index") {
    it("does not corrupt the array reference across a caught index expression") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val arr: Int[] = new Int[5]
          |    arr[0] = 10
          |    arr[1] = 20
          |    arr[2] = 30
          |    val y: Int = arr[try {
          |      Integer::parseInt("nope")
          |    } catch _: Exception {
          |      1
          |    }]
          |    return y + ""
          |  }
          |}
          |""".stripMargin,
        "TryInArrayIndexCaught.on",
        Array()
      )
      assert(Shell.Success("20") == result)
    }

    it("does not corrupt the array reference on the non-throwing path") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val arr: Int[] = new Int[5]
          |    arr[0] = 10
          |    arr[1] = 20
          |    arr[2] = 30
          |    val y: Int = arr[try {
          |      Integer::parseInt("2")
          |    } catch _: Exception {
          |      -1
          |    }]
          |    return y + ""
          |  }
          |}
          |""".stripMargin,
        "TryInArrayIndexOk.on",
        Array()
      )
      assert(Shell.Success("30") == result)
    }

    it("preserves left-to-right evaluation order around the spilled array reference") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static var log: String = ""
          |
          |  static def note(tag: String): String {
          |    Test::log = Test::log + tag
          |    return tag
          |  }
          |
          |  static def getArr(a: Int[]): Int[] {
          |    Test::note("G")
          |    return a
          |  }
          |
          |  static def boom(): Int {
          |    throw new Exception("boom")
          |  }
          |
          |  static def main(args: String[]): String {
          |    val arr: Int[] = new Int[3]
          |    arr[0] = 100
          |    arr[1] = 200
          |    val y: Int = Test::getArr(arr)[try { Test::note("T"); Test::boom() } catch _: Exception { 1 }]
          |    return Test::log + ":" + y
          |  }
          |}
          |""".stripMargin,
        "TryInArrayIndexOrder.on",
        Array()
      )
      assert(Shell.Success("GT:200") == result)
    }
  }
}
