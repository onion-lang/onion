package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for issue #230: a generic call in a closure body could not infer its
 * type arguments from the SAM's expected return type.
 *
 * `parse(s).flatMap((x) -> Result::ok(x * x))` used to fail with
 * `Function1[Int, Result[Int, String]] expected, Function1[Int, Result[Int,
 * Object]] used`: the closure body `Result::ok(x * x)` was typed with no
 * expected type, so `Result::ok`'s error type parameter E was left unbound and
 * defaulted to Object, which is not assignable to the expected
 * `Result[Int, String]` under invariant generics.
 *
 * The closure body's tail expression is now typed against the SAM's expected
 * return template (which still carries the outer method's unbound type
 * variables, e.g. `Result[U, String]`), so the already-known type arguments
 * (E = String) pin the inner generic call's type parameters.
 */
class ResultFlatMapInferenceSpec extends AbstractShellSpec {

  describe("generic call inference in a closure body") {
    it("infers the error type of Result::ok inside flatMap") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def parse(s: String): Result[Integer, String] {
          |    try { return Result::ok(Integer::parseInt(s)) }
          |    catch e: Exception { return Result::err("bad") }
          |  }
          |  static def main(args: String[]): Int {
          |    val r = Test::parse("3").flatMap((x) -> Result::ok(x * x))
          |    return r.getOrElse(-1)
          |  }
          |}
          |""".stripMargin,
        "ResultFlatMapOk.on",
        Array()
      )
      assert(Shell.Success(9) == result)
    }

    it("takes the error branch when the source Result is an error") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def parse(s: String): Result[Integer, String] {
          |    try { return Result::ok(Integer::parseInt(s)) }
          |    catch e: Exception { return Result::err("bad") }
          |  }
          |  static def main(args: String[]): Int {
          |    val r = Test::parse("oops").flatMap((x) -> Result::ok(x * x))
          |    return r.getOrElse(-7)
          |  }
          |}
          |""".stripMargin,
        "ResultFlatMapErrSource.on",
        Array()
      )
      assert(Shell.Success(-7) == result)
    }

    it("infers the error type when the closure body itself returns an error") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def parse(s: String): Result[Integer, String] {
          |    try { return Result::ok(Integer::parseInt(s)) }
          |    catch e: Exception { return Result::err("bad") }
          |  }
          |  static def main(args: String[]): Int {
          |    val r: Result[Integer, String] = Test::parse("3").flatMap((x) -> Result::err("nope"))
          |    return r.getOrElse(-99)
          |  }
          |}
          |""".stripMargin,
        "ResultFlatMapErrBody.on",
        Array()
      )
      assert(Shell.Success(-99) == result)
    }

    it("infers through a chain of flatMap calls") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def parse(s: String): Result[Integer, String] {
          |    try { return Result::ok(Integer::parseInt(s)) }
          |    catch e: Exception { return Result::err("bad") }
          |  }
          |  static def main(args: String[]): Int {
          |    val r = Test::parse("3").flatMap((x) -> Result::ok(x + 1)).flatMap((y) -> Result::ok(y * 10))
          |    return r.getOrElse(-1)
          |  }
          |}
          |""".stripMargin,
        "ResultFlatMapChain.on",
        Array()
      )
      assert(Shell.Success(40) == result)
    }

    it("still infers a plain map over a generic Result") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def parse(s: String): Result[Integer, String] {
          |    try { return Result::ok(Integer::parseInt(s)) }
          |    catch e: Exception { return Result::err("bad") }
          |  }
          |  static def main(args: String[]): Int {
          |    val r = Test::parse("5").map((x) -> x + 100)
          |    return r.getOrElse(-1)
          |  }
          |}
          |""".stripMargin,
        "ResultMap.on",
        Array()
      )
      assert(Shell.Success(105) == result)
    }

    it("infers the error type of Option::some inside flatMap") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val o: Option[Integer] = Option::some(7)
          |    val r = o.flatMap((x) -> Option::some(x * 2))
          |    return r.getOrElse(-1)
          |  }
          |}
          |""".stripMargin,
        "OptionFlatMap.on",
        Array()
      )
      assert(Shell.Success(14) == result)
    }
  }
}
