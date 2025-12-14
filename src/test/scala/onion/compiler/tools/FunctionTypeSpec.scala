package onion.compiler.tools

import onion.tools.Shell

class FunctionTypeSpec extends AbstractShellSpec {
  describe("Function type syntax") {
    it("accepts arrow function types in variable declarations") {
      val result = shell.run(
        """
          |class FunctionTypeArrowLocal {
          |public:
          |  static def main(args: String[]): String {
          |    f: (String) => String = #(x: String) { return x.toUpperCase(); }
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
          |  static def applyTwice(f: (String) => String, x: String): String {
          |    return f.call(f.call(x))
          |  }
          |
          |  static def main(args: String[]): String {
          |    f: (String) => String = #(x: String) { return x + "!"; }
          |    return FunctionTypeArrowParam::applyTwice(f, "a")
          |  }
          |}
          |""".stripMargin,
        "FunctionTypeArrowParam.on",
        Array()
      )
      assert(Shell.Success("a!!") == result)
    }
  }
}
