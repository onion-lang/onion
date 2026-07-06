package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for issue #279: a `do[Option]` bind from an under-determined empty source
 * such as `Option::none()` used to default its element type to Object, so later
 * arithmetic on the bound variable failed with E0001. The do-block's expected
 * element type `E` (from a `def f(): Option[E]` return or a `val o: Option[E]`
 * annotation) is now threaded into the bind so the bound variable is typed `E`.
 *
 * Assertions are on the main RETURN value (locale-independent).
 */
class DoNotationEmptyBindHintSpec extends AbstractShellSpec {

  describe("do[Option] none-only bind element-type hint (issue #279)") {
    it("infers the bind element type from the enclosing def return type") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def f(): Option[Int] = do[Option] {
          |    x <- Option::none()
          |    ret x + 1
          |  }
          |  static def main(args: String[]): Int {
          |    return f().getOrElse(-100)
          |  }
          |}
          |""".stripMargin,
        "DoNoneDefReturn.on",
        Array()
      )
      assert(Shell.Success(-100) == result)
    }

    it("infers the bind element type from a val type annotation") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val o: Option[Int] = do[Option] {
          |      x <- Option::none()
          |      ret x + 1
          |    }
          |    return o.getOrElse(-100)
          |  }
          |}
          |""".stripMargin,
        "DoNoneValTyped.on",
        Array()
      )
      assert(Shell.Success(-100) == result)
    }

    it("still infers a some-first bind without any hint") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val o: Option[Int] = do[Option] {
          |      x <- Option::some(5)
          |      ret x + 1
          |    }
          |    return o.getOrElse(-100)
          |  }
          |}
          |""".stripMargin,
        "DoSomeFirst.on",
        Array()
      )
      assert(Shell.Success(6) == result)
    }

    it("threads the hint through a none-only multi-bind block") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def f(): Option[Int] = do[Option] {
          |    x <- Option::none()
          |    y <- Option::none()
          |    ret x + y
          |  }
          |  static def main(args: String[]): Int {
          |    return f().getOrElse(-100)
          |  }
          |}
          |""".stripMargin,
        "DoNoneMultiBind.on",
        Array()
      )
      assert(Shell.Success(-100) == result)
    }

    it("keeps the `as Option[Int]` workaround working") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val o: Option[Int] = do[Option] {
          |      x <- (Option::none() as Option[Int])
          |      ret x + 1
          |    }
          |    return o.getOrElse(-100)
          |  }
          |}
          |""".stripMargin,
        "DoNoneCastWorkaround.on",
        Array()
      )
      assert(Shell.Success(-100) == result)
    }
  }

  describe("other monads keep working (issue #279 regression guard)") {
    it("do[Future] still infers from the def return type") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def g(): Future[Int] = do[Future] {
          |    x <- Future::successful(10)
          |    y <- Future::successful(20)
          |    ret x + y
          |  }
          |  static def main(args: String[]): Int {
          |    return g().getOrElse(-100)
          |  }
          |}
          |""".stripMargin,
        "DoFutureDefReturn.on",
        Array()
      )
      assert(Shell.Success(30) == result)
    }
  }
}
