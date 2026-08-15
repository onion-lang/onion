package onion.compiler.tools

import onion.tools.Shell

/**
 * A `try`/`catch` expression used as the right-hand operand of a binary
 * operator (arithmetic, bitwise, or comparison) crashed the compiler with an
 * `[I0000]` internal error during bytecode verification -- another sibling of
 * issue #669/#745/#747/#750: the JVM clears the operand stack when it
 * dispatches to an exception handler, so a left-hand operand already pushed
 * before the `try` was silently discarded on the exceptional path, desyncing
 * the merged stack shape from the normal path.
 *
 * `TermEmitter.emitBinaryTerm`/`emitComparison` now spill an already-pushed
 * left-hand operand into a local before evaluating a right-hand operand that
 * may run its own `try`, and reload it afterward -- mirroring
 * `AsmCodeGenerationVisitor.visitSetField`/`visitSetArray`.
 */
class TryInBinaryOperandSpec extends AbstractShellSpec {
  describe("try/catch expression as the right-hand operand of a binary operator") {
    it("does not corrupt the left operand of an arithmetic expression") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val ok: Int = 1 + (try { Integer::parseInt("7") } catch _: Exception { -1 })
          |    val caught: Int = 1 + (try { Integer::parseInt("nope") } catch _: Exception { -1 })
          |    return ok + "|" + caught
          |  }
          |}
          |""".stripMargin,
        "TryInBinaryArithmetic.on",
        Array()
      )
      assert(Shell.Success("8|0") == result)
    }

    it("does not corrupt the left operand of a comparison expression") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val ok: Boolean = 5 < (try { Integer::parseInt("7") } catch _: Exception { -1 })
          |    val caught: Boolean = 5 < (try { Integer::parseInt("nope") } catch _: Exception { -1 })
          |    return ok + "|" + caught
          |  }
          |}
          |""".stripMargin,
        "TryInBinaryComparison.on",
        Array()
      )
      assert(Shell.Success("true|false") == result)
    }

    it("does not corrupt a Long left operand across a try'd right operand") {
      val result = shell.run(
        """
          |import {
          |  java.lang.Long as JLong;
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val ok: Long = 10L + (try { JLong::parseLong("7") } catch _: Exception { -1L })
          |    val caught: Long = 10L + (try { JLong::parseLong("nope") } catch _: Exception { -1L })
          |    return ok + "|" + caught
          |  }
          |}
          |""".stripMargin,
        "TryInBinaryLong.on",
        Array()
      )
      assert(Shell.Success("17|9") == result)
    }

    it("preserves left-to-right evaluation order around the spilled left operand") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static var log: String = ""
          |
          |  static def note(tag: String): Int {
          |    Test::log = Test::log + tag
          |    return 1
          |  }
          |
          |  static def boom(): Int {
          |    throw new Exception("boom")
          |  }
          |
          |  static def main(args: String[]): String {
          |    val total: Int = Test::note("L") + (try { Test::note("R"); Test::boom() } catch _: Exception { 42 })
          |    return Test::log + ":" + total
          |  }
          |}
          |""".stripMargin,
        "TryInBinaryOrder.on",
        Array()
      )
      assert(Shell.Success("LR:43") == result)
    }
  }
}
