package onion.compiler.tools

import onion.tools.Shell

class FunctionTypeSpec extends AbstractShellSpec {
  describe("Function type syntax") {
    it("allows function call syntax sugar") {
      val result = shell.run(
        """
          |class FunctionCallSugar {
          |public:
          |  static def main(args: String[]): Int {
          |    val f: Int -> Int = (x: Int) -> { return x + 1; }
          |    return f(100)
          |  }
          |}
          |""".stripMargin,
        "FunctionCallSugar.on",
        Array()
      )
      assert(Shell.Success(101) == result)
    }

    it("accepts shorthand single-argument function types") {
      val result = shell.run(
        """
          |class FunctionTypeArrowShorthand {
          |public:
          |  static def main(args: String[]): String {
          |    val f: String -> String = (x: String) -> { return x.toUpperCase(); }
          |    return f.call("a")
          |  }
          |}
          |""".stripMargin,
        "FunctionTypeArrowShorthand.on",
        Array()
      )
      assert(Shell.Success("A") == result)
    }

    it("accepts arrow function types in variable declarations") {
      val result = shell.run(
        """
          |class FunctionTypeArrowLocal {
          |public:
          |  static def main(args: String[]): String {
          |    val f: (String) -> String = (x: String) -> { return x.toUpperCase(); }
          |    return f.call("a")
          |  }
          |}
          |""".stripMargin,
        "FunctionTypeArrowLocal.on",
        Array()
      )
      assert(Shell.Success("A") == result)
    }

    it("accepts arrow function types in method parameters") {
      val result = shell.run(
        """
          |class FunctionTypeArrowParam {
          |public:
          |  static def applyTwice(f: (String) -> String, x: String): String {
          |    return f.call(f.call(x))
          |  }
          |
          |  static def main(args: String[]): String {
          |    val f: (String) -> String = (x: String) -> { return x + "!"; }
          |    return FunctionTypeArrowParam::applyTwice(f, "a")
          |  }
          |}
          |""".stripMargin,
        "FunctionTypeArrowParam.on",
        Array()
      )
      assert(Shell.Success("a!!") == result)
    }

    it("accepts (args) -> { ... } lambda syntax") {
      val result = shell.run(
        """
          |class ArrowLambdaSyntax {
          |public:
          |  static def main(args: String[]): String {
          |    val f: (String) -> String = (x: String) -> { return x + "!"; }
          |    return f.call("a")
          |  }
          |}
          |""".stripMargin,
        "ArrowLambdaSyntax.on",
        Array()
      )
      assert(Shell.Success("a!") == result)
    }
  }
}
