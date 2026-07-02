package onion.compiler.tools

import onion.tools.Shell

/**
 * An immutable (`val`) nullable field is smart-cast after a null check, like a
 * local. A `var` field is not (it could be reassigned between check and use).
 */
class FieldSmartCastSpec extends AbstractShellSpec {
  describe("field smart cast") {
    it("narrows a val instance field after a null check") {
      val r = shell.run(
        """
          |class C {
          |  val name: String?
          |public:
          |  def this(n: String?) { name = n }
          |  def show(): Int { if name != null { return name.length() } else { return -1 } }
          |}
          |def main(args: String[]): Int { return new C("hello").show() - new C(null).show() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(6) == r)  // 5 - (-1)
    }
    it("narrows a static val field") {
      val r = shell.run(
        """
          |class C {
          |public:
          |  static val TAG: String? = "tag"
          |  static def len(): Int { if TAG != null { return TAG.length() } else { return -1 } }
          |}
          |def main(args: String[]): Int { return C::len() }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(3) == r)
    }
    it("does NOT narrow a var field") {
      val r = shell.run(
        """
          |class C {
          |  var name: String?
          |public:
          |  def this(n: String?) { name = n }
          |  def show(): Int { if name != null { return name.length() } else { return -1 } }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
  }
}
