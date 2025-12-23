package onion.compiler.tools

import onion.tools.Shell

class DefaultStaticImportSpec extends AbstractShellSpec {
  describe("Default static imports") {
    it("allows IO static calls without qualification") {
      val result = shell.run(
        """
          |class StaticImportSample {
          |public:
          |  static def main(args: String[]): String {
          |    println("hello")
          |    print("world")
          |    return "ok"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }
  }
}
