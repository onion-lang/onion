package onion.compiler.tools

import onion.tools.Shell

/** #2771: a failed `await()`/`awaitTimeout()` names the reason the task failed. */
class FutureAwaitCauseSpec extends AbstractShellSpec {

  private def run(body: String): Shell.Result =
    shell.run(
      s"""
         |import { onion.Future; java.lang.Integer; }
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |$body
         |  }
         |}
       """.stripMargin,
      "None",
      Array()
    )

  describe("Future.await() failure message (#2771)") {
    it("names the task's exception message") {
      val result = run(
        """    val f = Future::async { Integer::parseInt("nope") }
          |    try { f.await(); return "no error" } catch e: RuntimeException { return e.getMessage() }""".stripMargin
      )
      assert(result == Shell.Success("Future.await() failed (For input string: \"nope\")"))
    }

    it("keeps the task's exception as the cause") {
      val result = run(
        """    val f = Future::async { Integer::parseInt("nope") }
          |    try { f.await(); return "no error" } catch e: RuntimeException { return e.getCause().getClass().getSimpleName() }""".stripMargin
      )
      assert(result == Shell.Success("NumberFormatException"))
    }

    it("names the exception type when it has no message") {
      val result = run(
        """    val f = Future::failed[String](new IllegalStateException())
          |    try { f.await(); return "no error" } catch e: RuntimeException { return e.getMessage() }""".stripMargin
      )
      assert(result == Shell.Success("Future.await() failed (IllegalStateException)"))
    }

    it("names the root cause through a map") {
      val result = run(
        """    val f: Future[Integer] = Future::successful[String]("x").map((s: String) -> Integer::valueOf(Integer::parseInt(s)))
          |    try { f.await(); return "no error" } catch e: RuntimeException { return e.getMessage() }""".stripMargin
      )
      assert(result == Shell.Success("Future.await() failed (For input string: \"x\")"))
    }

    it("does the same for awaitTimeout(ms)") {
      val result = run(
        """    val f = Future::async { Integer::parseInt("nope") }
          |    try { f.awaitTimeout(5000L); return "no error" } catch e: RuntimeException { return e.getMessage() }""".stripMargin
      )
      assert(result == Shell.Success("Future.awaitTimeout() failed (For input string: \"nope\")"))
    }

    it("does not hang when the cause chain is a cycle (#2784)") {
      val result = run(
        """    val a = new RuntimeException("a")
          |    val b = new RuntimeException("b")
          |    a.initCause(b)
          |    b.initCause(a)
          |    val f = Future::failed[String](a)
          |    try { f.await(); return "no error" } catch e: RuntimeException { return e.getMessage() }""".stripMargin
      )
      assert(result == Shell.Success("Future.await() failed (b)"))
    }
  }
}
