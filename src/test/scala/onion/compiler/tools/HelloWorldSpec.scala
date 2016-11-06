package onion.compiler.tools

import onion.tools.Shell
import org.scalatest._

class HelloWorldSpec extends AbstractShellSpec {
  describe("HelloWorld class") {
    it("shows 'Hello, World'") {
      val resultHelloWorld = shell.run(
        """
          |class HelloWorld {
          |public:
          |  static def main(args: String[]): String {
          |    return "Hello, World";
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Hello, World") == resultHelloWorld)
    }
  }
}