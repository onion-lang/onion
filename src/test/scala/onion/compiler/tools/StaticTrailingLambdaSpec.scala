package onion.compiler.tools

import onion.tools.Shell

/**
 * A trailing lambda attaches to a static call without an empty argument list, the way it
 * already does to an instance call: `Future::async { -> compute() }` mirrors
 * `list.map { x -> x * 2 }`. Before this, only `Future::async() { -> ... }` parsed.
 */
class StaticTrailingLambdaSpec extends AbstractShellSpec {

  describe("a trailing lambda on a static call without parentheses") {
    it("is the only argument of a simple `Type::method` call") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val f: Future[Int] = Future::async { -> 21 * 2 }
          |    return f.await()
          |  }
          |}
          |""".stripMargin,
        "StaticTrailingLambda.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("binds lambda parameters and reaches a user-defined static method") {
      val result = shell.run(
        """
          |class Helper {
          |public:
          |  static def twice(f: Function1[Int, Int]): Int = f.call(f.call(1))
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return Helper::twice { x -> x + 1 }
          |  }
          |}
          |""".stripMargin,
        "StaticTrailingLambdaParams.on",
        Array()
      )
      assert(Shell.Success(3) == result)
    }

    it("works on a package-qualified type and inside do notation") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val r: Future[Int] = do[Future] {
          |      x <- onion.Future::async { -> 1 }
          |      y <- Future::async { ->
          |        x + 1
          |      }
          |      ret x + y
          |    }
          |    return r.await()
          |  }
          |}
          |""".stripMargin,
        "StaticTrailingLambdaDo.on",
        Array()
      )
      assert(Shell.Success(3) == result)
    }
  }
}
