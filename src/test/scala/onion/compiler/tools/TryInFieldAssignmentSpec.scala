package onion.compiler.tools

import onion.tools.Shell

/**
 * A `try`/`catch` expression assigned directly to a field or array element
 * used to crash the compiler with an `[I0000]` internal error during
 * bytecode verification (issue #745), the field/array-assignment sibling of
 * issue #669's call-argument bug: the JVM clears the operand stack when it
 * dispatches to an exception handler, so a receiver (or array ref/index)
 * already pushed before the `try` was silently discarded on the exceptional
 * path, desyncing the merged stack shape from the normal path — most visibly
 * inside a constructor, where the receiver is pushed before `super()`
 * returns.
 *
 * `AsmCodeGenerationVisitor.visitSetField`/`visitSetArray` now spill an
 * already-pushed target (and index) into locals before evaluating a value
 * that may run its own `try`, and reload them afterward.
 */
class TryInFieldAssignmentSpec extends AbstractShellSpec {
  describe("try/catch expression assigned to a field or array element") {
    it("does not corrupt the receiver of a field assignment inside a constructor (issue #745)") {
      val result = shell.run(
        """
          |class C {
          |  var _entries: List[Int]
          |
          |public:
          |  def this(fail: Boolean) {
          |    _entries = try {
          |      if fail { throw new Exception("boom") }
          |      [1, 2, 3]
          |    } catch e: Exception {
          |      [9, 9]
          |    }
          |  }
          |
          |  def entries(): List[Int] = _entries
          |}
          |
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val ok: C = new C(false)
          |    val caught: C = new C(true)
          |    return ok.entries().size + "|" + caught.entries().size
          |  }
          |}
          |""".stripMargin,
        "TryInConstructorFieldAssignment.on",
        Array()
      )
      assert(Shell.Success("3|2") == result)
    }

    it("preserves left-to-right evaluation order around the spilled receiver") {
      val result = shell.run(
        """
          |class Box {
          |public:
          |  var v: Int
          |}
          |class Test {
          |public:
          |  static var log: String = ""
          |
          |  static def note(tag: String): String {
          |    Test::log = Test::log + tag
          |    return tag
          |  }
          |
          |  static def getBox(b: Box): Box {
          |    Test::note("G")
          |    return b
          |  }
          |
          |  static def boom(): Int {
          |    throw new Exception("boom")
          |  }
          |
          |  static def main(args: String[]): String {
          |    val b: Box = new Box()
          |    Test::getBox(b).v = try { Test::note("T"); Test::boom() } catch _: Exception { 42 }
          |    return Test::log + ":" + b.v
          |  }
          |}
          |""".stripMargin,
        "TryInFieldAssignmentOrder.on",
        Array()
      )
      assert(Shell.Success("GT:42") == result)
    }

    it("does not corrupt the array/index of an array-element assignment") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val arr: Int[] = new Int[3]
          |    arr[0] = try { Integer::parseInt("7") } catch _: Exception { -1 }
          |    arr[1] = try { Integer::parseInt("nope") } catch _: Exception { -1 }
          |    return arr[0] + "|" + arr[1]
          |  }
          |}
          |""".stripMargin,
        "TryInArrayAssignment.on",
        Array()
      )
      assert(Shell.Success("7|-1") == result)
    }
  }
}
