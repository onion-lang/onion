package onion.compiler.tools

import onion.tools.Shell

class NullCastSpec extends AbstractShellSpec {
  describe("Null cast handling") {

    it("rejects null as Int at compile time") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    return null as Int
          |  }
          |}
          |""".stripMargin,
        "NullAsInt.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }
  }
}
