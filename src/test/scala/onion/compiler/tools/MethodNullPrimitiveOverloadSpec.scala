package onion.compiler.tools

import onion.tools.Shell

/** `null` never fills a primitive method parameter, so it picks the overload it can (#2876),
  * as `new K(null)` does. A lone primitive overload still resolves, then reports E0000. */
class MethodNullPrimitiveOverloadSpec extends AbstractShellSpec {
  private val cls =
    """
      |class K {
      |public:
      |  def this { }
      |  def f(n: Int): String = "I"
      |  def f(s: String?): String = "S"
      |  static def g(n: Int): String = "I"
      |  static def g(s: String?): String = "S"
      |}
    """.stripMargin

  describe("null against an Int overload and a String? overload") {
    it("instance call picks the String? overload") {
      val src = cls + "\ndef main(args: String[]): Int { IO::println(new K().f(null)); return 0 }\n"
      assert(Shell.Success(0) == shell.run(src, "None", Array()))
    }
    it("static call picks the String? overload") {
      val src = cls + "\ndef main(args: String[]): Int { IO::println(K::g(null)); return 0 }\n"
      assert(Shell.Success(0) == shell.run(src, "None", Array()))
    }
    it("a lone Int overload is still a compile error") {
      val src =
        """
          |class L {
          |public:
          |  def this { }
          |  def f(n: Int): String = "I"
          |}
          |def main(args: String[]): Int { IO::println(new L().f(null)); return 0 }
        """.stripMargin
      assert(Shell.Failure(-1) == shell.run(src, "None", Array()))
    }
    it("an Int argument still takes the Int overload") {
      val src = cls + "\ndef main(args: String[]): Int { IO::println(new K().f(1)); return 0 }\n"
      assert(Shell.Success(0) == shell.run(src, "None", Array()))
    }
  }
}
