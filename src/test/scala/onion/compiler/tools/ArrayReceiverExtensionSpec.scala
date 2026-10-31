package onion.compiler.tools

import onion.tools.Shell

class ArrayReceiverExtensionSpec extends AbstractShellSpec {
  private def run(body: String) =
    shell.run(
      s"""
         |extension String[] {
         |  def total(): Int = self.length
         |}
         |extension Int[] {
         |  def sum2(): Int = self[0] + self[1]
         |}
         |class Main {
         |public:
         |  static def main(args: String[]): String {
         |$body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("extension on an array type") {
    it("finds a String[] extension method") {
      assert(Shell.Success("3") == run("    val ss = new String[3]\n    return \"\" + ss.total()"))
    }

    it("finds an Int[] extension method") {
      assert(Shell.Success("5") == run("    val xs = new Int[2]\n    xs[0] = 2\n    xs[1] = 3\n    return \"\" + xs.sum2()"))
    }

    it("does not apply a String[] extension to an Integer[]") {
      assert(Shell.Failure(-1) == run("    val xs = new Integer[2]\n    return \"\" + xs.total()"))
    }

    it("does not apply an Int[] extension to a Long[]") {
      assert(Shell.Failure(-1) == run("    val xs = new Long[2]\n    return \"\" + xs.sum2()"))
    }
  }
}
