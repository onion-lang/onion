package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression for issue #256: a generic method's type variable must be inferred
 * from a non-closure argument even when an untyped-parameter lambda argument
 * comes BEFORE that argument.
 *
 * `apply2[A, B](f: Function1[A, B], x: A): B` called as
 * `apply2((s) -> s.length(), "hello")` used to fail with E0057 (type parameter
 * A may be null): the bidirectional-inference path resolved the closure's SAM
 * parameter type before A was pinned. It collapsed the resolved (non-closure)
 * arguments with `.filter(_ != null)`, which shifted the determining argument
 * `x = "hello"` onto the wrong formal (the closure's `Function1[A, B]`), so A
 * stayed unbound and `s` surfaced as a bare nullable type parameter.
 *
 * The resolved arguments now keep their original positions (closure slots stay
 * null and are skipped during unification), so A is inferred from `x` first
 * (A = String) regardless of argument order, and only then is the closure body
 * typed with `s: String`.
 *
 * A classless top-level script's synthesized `main` returns void, so results
 * are observed through a unique-keyed system property (System.out capture is not
 * safe under ScalaTest's parallel suites).
 */
class LambdaBeforeInferredArgSpec extends AbstractShellSpec {
  private def runReadingProperty(key: String, script: String): String = {
    System.clearProperty(key)
    shell.run(script, "None", Array())
    System.getProperty(key)
  }

  describe("type variable inferred from an argument after a preceding closure (#256)") {
    it("infers A from the second (non-closure) argument for a top-level function") {
      val v = runReadingProperty(
        "test256.toplevel",
        """
          |def apply2[A, B](f: Function1[A, B], x: A): B = f.call(x)
          |System::setProperty("test256.toplevel", "" + apply2((s) -> s.length(), "hello"))
          |""".stripMargin
      )
      assert(v == "5")
    }

    it("infers A for a static method inside a class") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def apply2[A, B](f: Function1[A, B], x: A): B = f.call(x)
          |  static def main(args: String[]): Integer = apply2((s) -> s.length(), "hello")
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(5) == result)
    }

    it("infers A for an instance method inside a class") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  def apply2[A, B](f: Function1[A, B], x: A): B = f.call(x)
          |  def go(): Integer = self.apply2((s) -> s.length(), "hello")
          |  static def main(args: String[]): Integer = new Test().go()
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(5) == result)
    }
  }

  describe("all previously-working variants still work (do not regress)") {
    it("still works with the determining argument FIRST") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def apply2[A, B](x: A, f: Function1[A, B]): B = f.call(x)
          |  static def main(args: String[]): Integer = apply2("hello", (s) -> s.length())
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(5) == result)
    }

    it("still works with explicit type arguments") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def apply2[A, B](f: Function1[A, B], x: A): B = f.call(x)
          |  static def main(args: String[]): Integer = apply2[String, Integer]((s) -> s.length(), "hello")
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(5) == result)
    }

    it("still works with a typed lambda parameter") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def apply2[A, B](f: Function1[A, B], x: A): B = f.call(x)
          |  static def main(args: String[]): Integer = apply2((s: String) -> s.length(), "hello")
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(5) == result)
    }
  }

  describe("mixed argument orders and multiplicities") {
    it("infers A when a closure sits between two determining arguments") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def pick[A](lo: A, f: Function1[A, Integer], hi: A): Integer = f.call(lo) + f.call(hi)
          |  static def main(args: String[]): Integer = pick("ab", (s) -> s.length(), "cdef")
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(6) == result)
    }

    it("infers A when two closures precede the determining argument") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def two[A, B, C](f: Function1[A, B], g: Function1[A, C], x: A): B = f.call(x)
          |  static def main(args: String[]): Integer = two((s) -> s.length(), (s) -> s, "hello")
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(5) == result)
    }
  }
}
