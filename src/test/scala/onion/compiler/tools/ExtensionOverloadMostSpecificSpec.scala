package onion.compiler.tools

import onion.tools.Shell

/**
 * Extension overloads on one receiver follow the class-method rule: when several apply,
 * the one whose parameters are all at least as specific wins (`ex(Char)` over `ex(Long)`
 * for `'A'`). Before, every such call was E0006 followed by a stray E0005.
 */
class ExtensionOverloadMostSpecificSpec extends AbstractShellSpec {
  private def eval(decls: String, body: String): Shell.Result =
    shell.run(
      s"""
         |extension String {
         |$decls
         |}
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |    $body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("extension overloads that differ in a parameter type") {
    it("picks ex(Char) over ex(Long) for a Char") {
      val decls =
        """  def ex(c: Char): String = "char:" + c
          |  def ex(l: Long): String = "long:" + l""".stripMargin
      assert(eval(decls, """return "x".ex('A')""") == Shell.Success("char:A"))
    }
    it("still picks the only applicable overload") {
      val decls =
        """  def ex(c: Char): String = "char:" + c
          |  def ex(l: Long): String = "long:" + l""".stripMargin
      assert(eval(decls, """return "x".ex(5L)""") == Shell.Success("long:5"))
    }
    it("picks the subtype parameter over Object") {
      val decls =
        """  def ey(s: String): String = "string"
          |  def ey(o: Object): String = "object"""".stripMargin
      assert(eval(decls, """return "x".ey("a")""") == Shell.Success("string"))
    }
  }
}
