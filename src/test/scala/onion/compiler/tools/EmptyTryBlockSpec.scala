package onion.compiler.tools

import onion.tools.Shell

/**
 * An empty `try {}` block must produce valid bytecode. It once emitted an
 * exception-table entry with start_pc == end_pc, which the JVM rejects at
 * class-load time with `ClassFormatError: Illegal exception table range`.
 * ControlFlowEmitter now guarantees the protected region is non-empty.
 */
class EmptyTryBlockSpec extends AbstractShellSpec {
  describe("empty try block") {
    it("compiles an empty try-catch to valid bytecode and never enters the handler") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 5
          |    try {
          |    } catch e: Exception {
          |      x = -1
          |    }
          |    return x
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(5) == result)
    }

    it("compiles an empty try-finally to valid bytecode and still runs finally") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var log: String = "start"
          |    try {
          |    } finally {
          |      log = "cleanup"
          |    }
          |    return log
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("cleanup") == result)
    }
  }
}
