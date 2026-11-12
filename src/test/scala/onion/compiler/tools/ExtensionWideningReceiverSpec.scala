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

  private def runAlone(src: String) = shell.run(src.stripMargin, "None", Array())

  describe("A widened extension never competes with a nearer one") {
    def supertypeVsLong(superName: String) = runAlone(
      s"""
         |extension $superName { def describe(): String = "$superName" }
         |extension Long { def describe(): String = "Long" }
         |class Main {
         |public:
         |  static def main(args: String[]): String {
         |    val i: Int = 5
         |    return i.describe()
         |  }
         |}
         |""")
    it("prefers extension Object over extension Long for an Int receiver") {
      assert(Shell.Success("Object") == supertypeVsLong("Object"))
    }
    it("prefers extension Number over extension Long for an Int receiver") {
      assert(Shell.Success("Number") == supertypeVsLong("Number"))
    }
    it("prefers extension Comparable over extension Long for an Int receiver") {
      assert(Shell.Success("Comparable") == supertypeVsLong("Comparable"))
    }
    it("prefers extension Number over extension Double for Int and Long receivers") {
      assert(Shell.Success("Number,Number") == runAlone(
        """
          |extension Number { def tag(): String = "Number" }
          |extension Double { def tag(): String = "Double" }
          |class Main {
          |public:
          |  static def main(args: String[]): String = 5.tag() + "," + 5L.tag()
          |}
          |"""))
    }
    it("prefers an exact extension Int over extension Long") {
      assert(Shell.Success("Int") == runAlone(
        """
          |extension Int { def describe(): String = "Int" }
          |extension Long { def describe(): String = "Long" }
          |class Main {
          |public:
          |  static def main(args: String[]): String = 5.describe()
          |}
          |"""))
    }
    it("prefers a member over a widened extension") {
      assert(Shell.Success("5") == runAlone(
        """
          |extension Long { def toString(): String = "Long" }
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val i: Int = 5
          |    return i.toString()
          |  }
          |}
          |"""))
    }
  }
}
