package onion.compiler.tools

import onion.tools.Shell

class TerminatorSpec extends AbstractShellSpec {
  describe("Newlines as statement terminators") {
    it("local variable declaration") {
      val result = shell.run(
        """
          | class LocalVar {
          | public:
          |   static def main(args: String[]): Int {
          |     val i: Int = 10
          |     val j: Int = 
          |       20
          |     return 0;
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(0) == result)
    }

    it("return statement") {
      val result = shell.run(
        """
          | class ReturnStatement {
          | public:
          |   static def main(args: String[]): Int {
          |     return 20
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(20) == result)
    }
  }
}
