package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression coverage for issue #317: an explicit lambda parameter type that
 * mismatches the expected function type must report a clean INCOMPATIBLE_TYPE
 * rather than leaking the synthetic `.call` method / the raw interface's
 * unsubstituted type variable. Exact-match and inferred-parameter lambdas must
 * still compile and run (the boxing/type-variable cases a naive check breaks).
 */
class LambdaParamMismatchSpec extends AbstractShellSpec {
  describe("lambda explicit parameter type checking") {
    it("rejects a String parameter where Int is expected") {
      assert(Shell.Failure(-1) == shell.run(
        """
          |val f: Function1[Int, Int] = (x: String) -> x
          |IO::println(f.call(1))
          |""".stripMargin,
        "None",
        Array()
      ))
    }

    it("accepts an exact-match Int parameter (primitive/boxed)") {
      assert(Shell.Success(10) == shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val f: Function1[Int, Int] = (x: Int) -> x * 2
          |    return f.call(5)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      ))
    }

    it("accepts an inferred parameter lambda") {
      assert(Shell.Success(10) == shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val f: Function1[Int, Int] = x -> x * 2
          |    return f.call(5)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      ))
    }
  }
}
