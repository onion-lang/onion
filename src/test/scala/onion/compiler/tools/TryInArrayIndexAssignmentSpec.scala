package onion.compiler.tools

import onion.tools.Shell

/**
 * A `try`/`catch` expression used as the index of an array-*write*
 * (`arr[...] = value`) crashed the compiler with an `[I0000]` internal
 * error during bytecode verification, the write-side sibling of
 * `TryInArrayIndexSpec`'s read-side fix: `visitSetArray` only spilled the
 * array reference into a local when the assigned *value* could run its own
 * `try`, never when the *index* could -- so the array reference pushed
 * before evaluating the index was still discarded by the JVM clearing the
 * operand stack on the exceptional path, desyncing the merged stack shape
 * from the normal path (the same family as #745/#669).
 *
 * `AsmCodeGenerationVisitor.visitSetArray` now also spills the array
 * reference when the index may run its own `try`.
 *
 * The final case below combines the index-side fix with the pre-existing
 * value-side handling: both the index and the assigned value run their own
 * `try`/`catch` in the same array write, which no earlier spec exercised
 * together. It already passes, so this closes a coverage gap rather than a
 * live bug.
 */
class TryInArrayIndexAssignmentSpec extends AbstractShellSpec {
  describe("try/catch expression as an array-write index") {
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
          |    arr[try {
          |      Integer::parseInt("nope")
          |    } catch _: Exception {
          |      1
          |    }] = 99
          |    return arr[0] + "," + arr[1] + "," + arr[2]
          |  }
          |}
          |""".stripMargin,
        "TryInArrayIndexAssignmentCaught.on",
        Array()
      )
      assert(Shell.Success("10,99,30") == result)
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
          |    arr[try {
          |      Integer::parseInt("2")
          |    } catch _: Exception {
          |      -1
          |    }] = 99
          |    return arr[0] + "," + arr[1] + "," + arr[2]
          |  }
          |}
          |""".stripMargin,
        "TryInArrayIndexAssignmentOk.on",
        Array()
      )
      assert(Shell.Success("10,20,99") == result)
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
          |  static def main(args: String[]): String {
          |    val arr: Int[] = new Int[3]
          |    arr[0] = 100
          |    arr[1] = 200
          |    Test::getArr(arr)[try { Test::note("T"); 1 } catch _: Exception { -1 }] = 999
          |    return Test::log + ":" + arr[0] + "," + arr[1]
          |  }
          |}
          |""".stripMargin,
        "TryInArrayIndexAssignmentOrder.on",
        Array()
      )
      assert(Shell.Success("GT:100,999") == result)
    }

    it("leaves the assigned value as the expression's result") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val arr: Int[] = new Int[3]
          |    val y: Int = arr[try {
          |      Integer::parseInt("nope")
          |    } catch _: Exception {
          |      0
          |    }] = 42
          |    return y + "," + arr[0]
          |  }
          |}
          |""".stripMargin,
        "TryInArrayIndexAssignmentResult.on",
        Array()
      )
      assert(Shell.Success("42,42") == result)
    }

    it("does not corrupt the array reference when both the index and the value run their own try/catch") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val arr: Int[] = new Int[3]
          |    arr[0] = 100
          |    arr[1] = 200
          |    arr[2] = 300
          |    arr[try { Integer::parseInt("1") } catch _: Exception { -1 }] =
          |      try { Integer::parseInt("nope") } catch _: Exception { 999 }
          |    arr[try { Integer::parseInt("nope") } catch _: Exception { 2 }] =
          |      try { Integer::parseInt("42") } catch _: Exception { -1 }
          |    return arr[0] + "," + arr[1] + "," + arr[2]
          |  }
          |}
          |""".stripMargin,
        "TryInArrayIndexAndValueAssignment.on",
        Array()
      )
      assert(Shell.Success("100,999,42") == result)
    }
  }
}
