package onion.compiler.tools

import onion.tools.Shell

/** #2724: an extension on a wider number type takes a narrower receiver, as an argument would. */
class ExtensionWideningReceiverSpec extends AbstractShellSpec {
  private def run(body: String) = shell.run(
    s"""
       |extension Long {
       |  def twice(): Long = this * 2L
       |}
       |extension Double {
       |  def half(): Double = this / 2.0
       |}
       |
       |class Main {
       |public:
       |  static def main(args: String[]): String {
       |    $body
       |  }
       |}
       |""".stripMargin, "None", Array())

  describe("An extension receiver widens") {
    it("takes an Int receiver for extension Long") {
      assert(Shell.Success("10") == run("""val n: Int = 5
                                          |return "" + n.twice()""".stripMargin))
    }
    it("takes a literal Int and an Int for extension Double") {
      assert(Shell.Success("2.5") == run("""return "" + 5.half()"""))
    }
    it("does not narrow: a Long receiver is not an Int extension") {
      val r = shell.run(
        """
          |extension Int {
          |  def inc(): Int = this + 1
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String = "" + 5L.inc()
          |}
          |""".stripMargin, "None", Array())
      assert(r == Shell.Failure(-1))
    }
  }
}
