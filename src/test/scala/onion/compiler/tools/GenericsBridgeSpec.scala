package onion.compiler.tools

import onion.tools.Shell

class GenericsBridgeSpec extends AbstractShellSpec {
  describe("Generics bridge methods") {
    it("dispatches through a generic superclass reference") {
      val result = shell.run(
        """
          |class Base[T extends Object] {
          |public:
          |  def get(x: T): T {
          |    return x
          |  }
          |}
          |
          |class Sub : Base[String] {
          |public:
          |  def get(x: String): String {
          |    return x + "!"
          |  }
          |
          |  static def main(args: String[]): String {
          |    val b: Base[String] = new Sub
          |    return b.get("a")
          |  }
          |}
          |""".stripMargin,
        "Bridge.on",
        Array()
      )
      assert(Shell.Success("a!") == result)
    }
  }
}
