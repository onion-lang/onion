package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for issue #233: a throw-only lambda body (a closure that never returns
 * normally) used to infer an Object element type when passed to a generative
 * generic method, e.g. `Future::async(() -> { throw ... })` produced
 * `Future[Object]` and failed to match the expected `Future[String]`.
 *
 * The closure is now routed through bidirectional inference so the expected
 * parameter/return type pins the type variable before the bottom body is
 * widened to Object.
 */
class ThrowOnlyClosureSpec extends AbstractShellSpec {

  describe("throw-only closures against generic methods") {
    it("infers the expected element type for Future::async") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f: Future[String] = Future::async(() -> { throw new RuntimeException("x") })
          |    return "compiled"
          |  }
          |}
          |""".stripMargin,
        "ThrowOnlyAsync.on",
        Array()
      )
      assert(Shell.Success("compiled") == result)
    }

    it("honors an explicit type argument with a throw-only body") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f: Future[String] = Future::async[String](() -> { throw new RuntimeException("x") })
          |    return "compiled"
          |  }
          |}
          |""".stripMargin,
        "ThrowOnlyAsyncExplicit.on",
        Array()
      )
      assert(Shell.Success("compiled") == result)
    }

    it("infers through a user-defined generic method") {
      val result = shell.run(
        """
          |class Box {
          |public:
          |  static def make[T](f: Function0[T]): T {
          |    return null
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s: String = Box::make(() -> { throw new RuntimeException("x") })
          |    return "compiled"
          |  }
          |}
          |""".stripMargin,
        "ThrowOnlyBox.on",
        Array()
      )
      assert(Shell.Success("compiled") == result)
    }

    it("accepts a throw-only body whose paths all complete abruptly") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f: Future[String] = Future::async(() -> {
          |      if true { throw new RuntimeException("a") } else { throw new RuntimeException("b") }
          |    })
          |    return "compiled"
          |  }
          |}
          |""".stripMargin,
        "ThrowOnlyIfElse.on",
        Array()
      )
      assert(Shell.Success("compiled") == result)
    }

    it("still infers a normal value-returning body") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f: Future[Int] = Future::async(() -> { 42 })
          |    return "compiled"
          |  }
          |}
          |""".stripMargin,
        "ValueBodyAsync.on",
        Array()
      )
      assert(Shell.Success("compiled") == result)
    }

    it("reports a clean error for a wrong-arity explicit type argument") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f: Future[String] = Future::async[String, Int](() -> { throw new RuntimeException("x") })
          |    return "compiled"
          |  }
          |}
          |""".stripMargin,
        "ThrowOnlyWrongArity.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
