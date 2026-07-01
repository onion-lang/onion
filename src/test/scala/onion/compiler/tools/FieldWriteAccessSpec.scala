package onion.compiler.tools

import onion.tools.Shell

/**
 * Writing a non-public field from outside its class must be rejected (E0014),
 * as reads already are. Previously the write silently fell through to a no-op
 * with no access or type check.
 */
class FieldWriteAccessSpec extends AbstractShellSpec {
  private def fails(p: String): Boolean = shell.run(p, "None", Array()) == Shell.Failure(-1)

  describe("field write access control") {
    it("rejects writing a non-public field from outside") {
      assert(fails(
        """
          |class Plain {
          |  var value: String
          |public:
          |  def this(v: String) { value = v }
          |  def get(): String { return value }
          |}
          |val p = new Plain("orig")
          |p.value = "changed"
          |""".stripMargin))
    }
    it("still allows writing a public field and updating it") {
      val r = shell.run(
        """
          |class Pub {
          |public:
          |  var value: String
          |  def this(v: String) { value = v }
          |  static def main(args: String[]): String {
          |    val p = new Pub("orig")
          |    p.value = "changed"
          |    return p.value
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("changed") == r)
    }
    it("still allows internal writes without this.") {
      val r = shell.run(
        """
          |class C {
          |  var n: Int
          |public:
          |  def this { n = 0 }
          |  def bump(): void { n = n + 1 }
          |  def get(): Int { return n }
          |  static def main(args: String[]): Int {
          |    val c = new C()
          |    c.bump()
          |    c.bump()
          |    return c.get()
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(2) == r)
    }
  }
}
