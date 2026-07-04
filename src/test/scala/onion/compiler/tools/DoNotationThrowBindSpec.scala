package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for issue #284: in `do[Future]`, a bind whose RHS is a throw-only lambda
 * (its body infers to bottom, widened to Object) used to leave the bound variable
 * typed Object, breaking `+` / `ret` on it. When the do block's result type is
 * declared (`val r: Future[E] = do[Future] { ... }`), the element type `E` is now
 * threaded into the throw-only bind so the bound variable is typed `E`.
 *
 * Assertions are on the main RETURN value (locale-independent).
 */
class DoNotationThrowBindSpec extends AbstractShellSpec {

  describe("do[Future] with a throw-only bind (issue #284)") {
    it("types the bound var from the declared element type so `+` type-checks") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val r: Future[Int] = do[Future] {
          |      b <- Future::async(() -> { throw new RuntimeException("mid fail") })
          |      ret b + 1
          |    }
          |    return r.getOrElse(-100)
          |  }
          |}
          |""".stripMargin,
        "DoThrowBindPlus.on",
        Array()
      )
      assert(Shell.Success(-100) == result)
    }

    it("types `ret b` alone against the declared element type") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val r: Future[Int] = do[Future] {
          |      b <- Future::async(() -> { throw new RuntimeException("x") })
          |      ret b
          |    }
          |    return r.getOrElse(-100)
          |  }
          |}
          |""".stripMargin,
        "DoThrowBindRet.on",
        Array()
      )
      assert(Shell.Success(-100) == result)
    }

    it("keeps a value bind usable alongside a later throw-only bind") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val r: Future[Int] = do[Future] {
          |      a <- Future::async(() -> 10)
          |      b <- Future::async(() -> { throw new RuntimeException("x") })
          |      ret a + b
          |    }
          |    return r.getOrElse(-100)
          |  }
          |}
          |""".stripMargin,
        "DoThrowBindMulti.on",
        Array()
      )
      assert(Shell.Success(-100) == result)
    }

    it("does not disturb a fully value-terminated do block") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val r: Future[Int] = do[Future] {
          |      a <- Future::async(() -> 10)
          |      b <- Future::async(() -> 20)
          |      ret a + b
          |    }
          |    return r.getOrElse(-100)
          |  }
          |}
          |""".stripMargin,
        "DoThrowBindSuccess.on",
        Array()
      )
      assert(Shell.Success(30) == result)
    }
  }

  describe("standalone throw-only async still works (issue #284 regression guard)") {
    it("infers the element type from the declared Future[Int]") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val r: Future[Int] = Future::async(() -> { throw new RuntimeException("x") })
          |    return r.getOrElse(-100)
          |  }
          |}
          |""".stripMargin,
        "StandaloneThrowAsync.on",
        Array()
      )
      assert(Shell.Success(-100) == result)
    }
  }
}
