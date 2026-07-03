package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression for #232 (final sub-case): a lambda with untyped parameters passed
 * to an UNQUALIFIED call — a top-level function or a bare method on the current
 * class. The unqualified path used to type every argument eagerly, so the
 * untyped closure failed with E0052 before the target overload was resolved.
 * It now mirrors the instance/static paths: it detects untyped-parameter
 * closures, resolves the overload against the current class (instance or static)
 * or the synthetic top-level class first, then types each closure against its
 * resolved functional-interface parameter type.
 *
 * A classless top-level script's synthesized `main` returns void, so results are
 * observed through a unique-keyed system property (System.out capture is not
 * safe under ScalaTest's parallel suites).
 */
class UnqualifiedClosureArgSpec extends AbstractShellSpec {
  private def runReadingProperty(key: String, script: String): String = {
    System.clearProperty(key)
    shell.run(script, "None", Array())
    System.getProperty(key)
  }

  describe("unqualified call with an untyped-parameter closure argument") {
    it("resolves a top-level function called unqualified (#232)") {
      val v = runReadingProperty(
        "test232.toplevel",
        """
          |def applyF(f: Function1[Integer, Integer], x: Integer): Integer = f.call(x)
          |System::setProperty("test232.toplevel", "" + applyF((n) -> n * 3, 5))
          |""".stripMargin
      )
      assert(v == "15")
    }

    it("resolves a bare static-method call inside a class") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def applyF(f: Function1[Integer, Integer], x: Integer): Integer = f.call(x)
          |  static def main(args: String[]): Integer = applyF((n) -> n * 3, 5)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(15) == result)
    }

    it("resolves a bare instance-method call inside a class (non-static context)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  def applyF(f: Function1[Integer, Integer], x: Integer): Integer = f.call(x)
          |  def go(): Integer = applyF((n) -> n * 3, 7)
          |  static def main(args: String[]): Integer = new Test().go()
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(21) == result)
    }

    it("still works with a closure as the only argument") {
      val v = runReadingProperty(
        "test232.only",
        """
          |def run0(f: Function0[Integer]): Integer = f.call()
          |System::setProperty("test232.only", "" + run0(() -> 42))
          |""".stripMargin
      )
      assert(v == "42")
    }

    it("handles nested unqualified calls with closures") {
      val v = runReadingProperty(
        "test232.nested",
        """
          |def applyF(f: Function1[Integer, Integer], x: Integer): Integer = f.call(x)
          |System::setProperty("test232.nested", "" + applyF((n) -> applyF((m) -> m + 1, n), 5))
          |""".stripMargin
      )
      assert(v == "6")
    }
  }

  describe("unqualified resolution is otherwise unchanged") {
    it("a typed-parameter lambda argument to a top-level function still works") {
      val v = runReadingProperty(
        "test232.typed",
        """
          |def applyF(f: Function1[Integer, Integer], x: Integer): Integer = f.call(x)
          |System::setProperty("test232.typed", "" + applyF((n: Integer) -> n * 3, 5))
          |""".stripMargin
      )
      assert(v == "15")
    }

    it("a top-level function with normal (non-closure) args still works") {
      val v = runReadingProperty(
        "test232.normal",
        """
          |def add(a: Integer, b: Integer): Integer = a + b
          |System::setProperty("test232.normal", "" + add(3, 4))
          |""".stripMargin
      )
      assert(v == "7")
    }

    it("a bare static-import call (println) still works") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    println("bare-println")
          |    return "ok"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("a callable value invoked directly still works") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Integer {
          |    val f = (x: Integer) -> x + 1
          |    return f(41)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(42) == result)
    }
  }
}
