package onion.compiler.tools

import onion.tools.Shell

/** A reference-type test on a primitive value tests the boxed value (#2726); it used to be a VerifyError. */
class PrimitiveTypeTestSpec extends AbstractShellSpec {
  private def run(body: String) =
    shell.run(
      s"""
         |import { java.lang.Long as JLong; }
         |class T {
         |public:
         |  static def main(args: String[]): String {
         |$body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("is on a primitive operand") {
    it("tests the boxed value") {
      assert(run("val x: Long = 5L\n return \"\" + (x is JLong) + (x is String)") == Shell.Success("truefalse"))
    }
  }

  describe("a type pattern on a primitive scrutinee") {
    it("matches the boxed value and binds it") {
      assert(run("val x: Long = 5L\n return select x { case n is JLong: \"boxed \" + n; else: \"other\" }") == Shell.Success("boxed 5"))
    }
    it("does not match an unrelated reference type") {
      assert(run("val i: Int = 5\n return select i { case s is String: \"str\"; else: \"other\" }") == Shell.Success("other"))
    }
  }
}
