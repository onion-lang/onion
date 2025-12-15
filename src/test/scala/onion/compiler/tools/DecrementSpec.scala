package onion.compiler.tools

import onion.tools.Shell

class DecrementSpec extends AbstractShellSpec {
  describe("Decrement class") {
    it("demonstrate decrement(--) feature") {
      val result = shell.run(
        """
          | class Decrement {
          | public:
          |   static def main(args: String[]): Int {
          |     var i: Int = 10;
          |     for ; i >= 0; i-- { }
          |     return i;
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(-1) == result)
    }
  }
}
