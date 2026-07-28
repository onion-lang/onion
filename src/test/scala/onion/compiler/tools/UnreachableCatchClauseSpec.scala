package onion.compiler.tools

import onion.tools.Shell

/**
 * A later `catch` clause whose exception type is already covered by an earlier
 * clause (a supertype, or the same type again) can never run. javac rejects this
 * at compile time; Onion silently accepted it and ran only the first matching
 * clause. E0083 reports it instead (issue found during routine maintenance).
 */
class UnreachableCatchClauseSpec extends AbstractShellSpec {

  private def failsWith(code: String, source: String): Unit = {
    val buf = new java.io.ByteArrayOutputStream()
    val ps = new java.io.PrintStream(buf, true, "UTF-8")
    val (o, e) = (System.out, System.err)
    val r =
      try {
        System.setOut(ps); System.setErr(ps)
        Console.withOut(ps) { Console.withErr(ps) { shell.run(source, "None", Array()) } }
      } finally { System.setOut(o); System.setErr(e) }
    val out = new String(buf.toByteArray, "UTF-8")
    assert(r.isInstanceOf[Shell.Failure], s"expected a compile failure for $code, got $r\n$out")
    assert(out.contains(code), s"expected $code in diagnostics, got:\n$out")
  }

  describe("E0083 unreachable catch clause") {
    it("a supertype caught earlier shadows a subtype caught later") {
      failsWith(
        "E0083",
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    try {
          |      throw new IllegalArgumentException("x")
          |    } catch e: RuntimeException {
          |      IO::println("runtime")
          |    } catch e: IllegalArgumentException {
          |      IO::println("iae")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      )
    }

    it("the exact same exception type caught twice shadows the second clause") {
      failsWith(
        "E0083",
        """class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    try {
          |      throw new Exception("x")
          |    } catch e: Exception {
          |      IO::println("first")
          |    } catch e: Exception {
          |      IO::println("second")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin
      )
    }

    it("does not flag unrelated exception types") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    try {
          |      throw new IllegalStateException("x")
          |    } catch e: IllegalArgumentException {
          |      IO::println("iae")
          |    } catch e: IllegalStateException {
          |      IO::println("ise")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin,
        "OkOrder.on",
        Array()
      )
      assert(Shell.Success(0) == result)
    }

    it("does not flag a subtype caught before its supertype") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    try {
          |      throw new IllegalArgumentException("x")
          |    } catch e: IllegalArgumentException {
          |      IO::println("iae")
          |    } catch e: RuntimeException {
          |      IO::println("runtime")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin,
        "OkNarrowFirst.on",
        Array()
      )
      assert(Shell.Success(0) == result)
    }

    it("does not flag the alternatives of a single multi-catch clause against each other") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    try {
          |      throw new IllegalArgumentException("x")
          |    } catch e: IllegalArgumentException | RuntimeException {
          |      IO::println("multi")
          |    }
          |    return 0
          |  }
          |}
          |""".stripMargin,
        "OkMultiCatch.on",
        Array()
      )
      assert(Shell.Success(0) == result)
    }

    it("does not flag a multi-catch clause listing a supertype before a subtype") {
      // The risky ordering: RuntimeException is written before its subtype
      // IllegalStateException, in the SAME `A | B` clause. This exercises
      // Rewriting's per-alternative `block.copy(...)`, which gives the two
      // desugared entries distinct BlockExpression objects even though they
      // share one source clause (regression: `eq` on the body missed this).
      val result = shell.run(
        """
          |def f(): List[Int] = try { [1] } catch e: RuntimeException | IllegalStateException { [] }
          |def main(args: String[]): Int { return f().size() }
          |""".stripMargin,
        "OkMultiCatchSupertypeFirst.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }
  }
}
