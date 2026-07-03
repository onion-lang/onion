package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for issue #232 (method-argument sub-case): the expected parameter type
 * is now propagated into a generic call that appears in argument position, so
 * its type arguments are pinned instead of defaulting to Object.
 *
 * `take(Result::ok(7))` where `take` expects `Result[Integer, String]` used to
 * fail with `take(Result[Int, Object]) not found`: the argument `Result::ok(7)`
 * was typed with no expected type, so `Result::ok`'s error type parameter E was
 * left unbound and defaulted to Object, which is not assignable to the expected
 * `Result[Integer, String]` under invariant generics.
 *
 * When resolution finds no applicable method, the compiler now narrows the
 * candidates to a single one by name and arity, re-types the malleable
 * arguments (a generic static/unqualified call, or a collection literal)
 * against that candidate's parameter types, and retries resolution.
 */
class MethodArgExpectedTypeSpec extends AbstractShellSpec {

  describe("expected-type propagation into a method argument") {
    it("pins the error type of Result::ok passed to a top-level function") {
      val result = shell.run(
        """
          |def take(r: Result[Integer, String]): Int = r.getOrElse(-1)
          |def main(args: String[]): Int { return take(Result::ok(7)) }
          |""".stripMargin,
        "TopLevelResultArg.on",
        Array()
      )
      assert(Shell.Success(7) == result)
    }

    it("pins Result::ok passed to a generic context-bound function (#232 repro)") {
      val result = shell.run(
        """
          |trait Show[T] { def show(x: T): String }
          |instance Show[Integer] { def show(x: Integer): String = "n" + x }
          |def render[T: Show](r: Result[T, String]): String = r.map((x) -> Show[T]::show(x)).getOrElse("err")
          |def main(args: String[]): String { return render(Result::ok(42)) }
          |""".stripMargin,
        "RenderResultArg.on",
        Array()
      )
      assert(Shell.Success("n42") == result)
    }

    it("pins the error type of Result::ok passed to an instance method") {
      val result = shell.run(
        """
          |class Box {
          |public:
          |  def unwrap(r: Result[Integer, String]): Int = r.getOrElse(-1)
          |}
          |def main(args: String[]): Int {
          |  val b = new Box()
          |  return b.unwrap(Result::ok(11))
          |}
          |""".stripMargin,
        "InstanceResultArg.on",
        Array()
      )
      assert(Shell.Success(11) == result)
    }

    it("pins the error type of Result::ok passed to a static method") {
      val result = shell.run(
        """
          |class Helper {
          |public:
          |  static def take(r: Result[Integer, String]): Int = r.getOrElse(-1)
          |}
          |def main(args: String[]): Int { return Helper::take(Result::ok(5)) }
          |""".stripMargin,
        "StaticResultArg.on",
        Array()
      )
      assert(Shell.Success(5) == result)
    }

    it("pins Option::some passed as an argument") {
      val result = shell.run(
        """
          |def unwrap(o: Option[Integer]): Int = o.getOrElse(-1)
          |def main(args: String[]): Int { return unwrap(Option::some(9)) }
          |""".stripMargin,
        "OptionSomeArg.on",
        Array()
      )
      assert(Shell.Success(9) == result)
    }

    it("takes the error branch when the argument is Result::err") {
      val result = shell.run(
        """
          |def take(r: Result[Integer, String]): Int = r.getOrElse(-3)
          |def main(args: String[]): Int { return take(Result::err("bad")) }
          |""".stripMargin,
        "ResultErrArg.on",
        Array()
      )
      assert(Shell.Success(-3) == result)
    }
  }

  describe("existing resolution is preserved") {
    it("still resolves an ordinary overloaded call") {
      val result = shell.run(
        """
          |def g(x: Integer): String = "int"
          |def g(x: String): String = "str"
          |def main(args: String[]): String { return g(3) + g("x") }
          |""".stripMargin,
        "OverloadPreserved.on",
        Array()
      )
      assert(Shell.Success("intstr") == result)
    }

    it("still reports an ambiguous overloaded call") {
      val result = shell.run(
        """
          |def f(x: Integer): Int = 1
          |def f(x: String): Int = 2
          |def main(args: String[]): Int { return f(null) }
          |""".stripMargin,
        "AmbiguousPreserved.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("still reports a genuine not-found call") {
      val result = shell.run(
        """
          |def take(r: Result[Integer, String]): Int = r.getOrElse(-1)
          |def main(args: String[]): Int { return take("not a result") }
          |""".stripMargin,
        "NotFoundPreserved.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("still resolves a non-generic argument") {
      val result = shell.run(
        """
          |def take(r: Result[Integer, String]): Int = r.getOrElse(-1)
          |def make(): Result[Integer, String] = Result::ok(4)
          |def main(args: String[]): Int { return take(make()) }
          |""".stripMargin,
        "NonGenericArg.on",
        Array()
      )
      assert(Shell.Success(4) == result)
    }
  }
}
