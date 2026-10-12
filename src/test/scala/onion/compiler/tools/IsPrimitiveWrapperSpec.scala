package onion.compiler.tools

import onion.tools.Shell

class IsPrimitiveWrapperSpec extends AbstractShellSpec {
  private def run(body: String) =
    shell.run(
      s"""
         |class IsPrim {
         |public:
         |  static def main(args: String[]): String {
         |$body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("`is` against a primitive type name") {
    it("checks the wrapper class of an Object value") {
      val r = run(
        """    val a: Object = 1L
          |    val b: Object = 2
          |    val c: Object = 1.5
          |    val d: Object = true
          |    val e: Object = "s"
          |    return "" + (a is Long) + (a is Int) + (b is Int) + (c is Double) + (d is Boolean) + (e is Long)""".stripMargin)
      assert(Shell.Success("truefalsetruetruetruefalse") == r)
    }

    it("is true for a primitive-typed operand of the same type") {
      val r = run(
        """    val n: Int = 3
          |    return "" + (n is Int) + (n is Long)""".stripMargin)
      assert(Shell.Success("truefalse") == r)
    }
  }
}
