package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for lambda ergonomics (issue #120): expression bodies,
 * parameter type inference from the expected function type, the bare
 * single-parameter form, and function-call syntax on function values.
 */
class LambdaErgonomicsSpec extends AbstractShellSpec {

  describe("Lambda expression bodies") {
    it("supports an expression body with typed parameters") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val f = (x: Int) -> x * 2
          |    return f(21)
          |  }
          |}
          |""".stripMargin,
        "ExprBodyLambda.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("supports multi-parameter expression bodies") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val add = (a: Int, b: Int) -> a + b
          |    return add(40, 2)
          |  }
          |}
          |""".stripMargin,
        "MultiParamExprBody.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("still supports block bodies") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val f = (x: Int) -> { return x + 1; }
          |    return f(41)
          |  }
          |}
          |""".stripMargin,
        "BlockBodyLambda.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }
  }

  describe("Lambda parameter type inference") {
    it("infers parenthesized parameter types from the expected function type") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val f: Int -> Int = (x) -> x + 1
          |    return f(41)
          |  }
          |}
          |""".stripMargin,
        "InferredParamLambda.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("supports the bare single-parameter form") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val f: Int -> Int = x -> x * 10
          |    return f(5)
          |  }
          |}
          |""".stripMargin,
        "BareParamLambda.on",
        Array()
      )
      assert(Shell.Success(50) == result)
    }
  }

  describe("Function call syntax on values") {
    it("calls function values directly and passes them to functions") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def apply(g: Int -> Int, v: Int): Int { return g(v) }
          |  static def main(args: String[]): Int {
          |    val f = (x: Int) -> x * 2
          |    return f(20) + apply(f, 1)
          |  }
          |}
          |""".stripMargin,
        "CallableValue.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }
  }
}
