package onion.compiler.tools

import onion.tools.Shell

class FunctionWithExpressionBodySpec extends AbstractShellSpec {
  describe("ExpressionBody class") {
    it("returns String a value") {
      val resultExpressionBody = shell.run(
        """
          |class ExpressionBody {
          |public:
          |  static def main(args: String[]): String = "ExpressionBody"
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ExpressionBody") == resultExpressionBody)
    }
  }

  describe("top-level function with an expression body") {
    // Regression: the expression-body branch named the function after the
    // `def` keyword instead of its identifier, so it was registered as "def"
    // and any call by its real name failed to resolve (E0005).
    it("is callable by its declared name (not misnamed 'def')") {
      val result = shell.run(
        """
          |def greet(name: String): String = "hi " + name
          |IO::println(greet("bob"))
        """.stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }

    it("works with a generic (List) parameter type") {
      val result = shell.run(
        """
          |def firstOf(list: List[String]): String = list[0]
          |IO::println(firstOf(["a", "b", "c"]))
        """.stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }
  }

  describe("top-level generic function (issue #163)") {
    it("declares and calls a type parameter with an explicit type argument") {
      val result = shell.run(
        """
          |def first[T](list: List[T]): T = list[0]
          |IO::println(first[String](["a", "b", "c"]))
        """.stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }

    it("infers the type argument from the call") {
      val result = shell.run(
        """
          |def first[T](list: List[T]): T = list[0]
          |IO::println(first(["p", "q"]))
        """.stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }

    it("supports multiple type parameters") {
      val result = shell.run(
        """
          |def pairUp[A, B](a: A, b: B): String = a + ":" + b
          |IO::println(pairUp[String, Int]("x", 5))
        """.stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }
  }
}