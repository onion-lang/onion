package onion.compiler.tools

import onion.tools.Shell

class IncrementSpec extends AbstractShellSpec {
  describe("Increment class") {
    it("demonstrate increment(++) feature") {
      val result = shell.run(
        """
          | class Increment {
          | public:
          |   static def main(args: String[]): Int {
          |     i = 0;
          |     for i = 0; i < 10; i++ { }
          |     return i;
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(10) == result)
    }
  }
}