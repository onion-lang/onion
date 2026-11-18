package onion.compiler.tools

import onion.tools.Shell

/** `copy(..)` treats named arguments as an ordinary call does (#3100). */
class RecordCopyNamedArgumentSpec extends AbstractShellSpec {
  describe("record copy with named arguments") {
    it("rejects a name given twice (E0044), so no argument is dropped unrun") {
      val result = shell.run(
        """
          |record R(a: String, b: String)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r = new R("x", "y").copy(a = "1", a = "2")
          |    return r.a()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(result == Shell.Failure(-1))
    }

    it("binds a named argument to the record's own copy by parameter name") {
      val result = shell.run(
        """
          |record Q(a: String, b: String) {
          |public:
          |  def copy(b: String, a: String): Q = new Q(a, b)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return new Q("x", "y").copy(a = "A", b = "B").toString()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(result == Shell.Success("Q(a=A, b=B)"))
    }

    it("still partially copies with the generated copy") {
      val result = shell.run(
        """
          |record R(a: String, b: String)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return new R("x", "y").copy(b = "B").toString()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(result == Shell.Success("R(a=x, b=B)"))
    }
  }
}
