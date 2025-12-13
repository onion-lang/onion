package onion.compiler.tools

import onion.tools.Shell

class GenericsInterfaceBridgeSpec extends AbstractShellSpec {
  describe("Generics interface bridge methods") {
    it("dispatches through a generic interface reference") {
      val result = shell.run(
        """
          |interface I[T extends Object] {
          |  def get(x: T): T
          |}
          |
          |class Impl <: I[String] {
          |public:
          |  def get(x: String): String {
          |    return x + "!"
          |  }
          |
          |  static def main(args: String[]): String {
          |    i: I[String] = new Impl
          |    return i.get("a")
          |  }
          |}
          |""".stripMargin,
        "InterfaceBridge.on",
        Array()
      )
      assert(Shell.Success("a!") == result)
    }
  }
}

