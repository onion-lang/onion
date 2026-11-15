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
          |class Impl conforms I[String] {
          |public:
          |  def get(x: String): String {
          |    return x + "!"
          |  }
          |
          |  static def main(args: String[]): String {
          |    val i: I[String] = new Impl
          |    return i.get("a")
          |  }
          |}
          |""".stripMargin,
        "InterfaceBridge.on",
        Array()
      )
      assert(Shell.Success("a!") == result)
    }

    it("bridges a `T?` parameter implemented as `String?` (#2719)") {
      val result = shell.run(
        """
          |interface I[T] {
          |  def g(x: T?): String
          |}
          |
          |class Impl conforms I[String] {
          |public:
          |  def g(x: String?): String {
          |    return "got " + x
          |  }
          |
          |  static def main(args: String[]): String {
          |    val i: I[String] = new Impl
          |    return i.g("a")
          |  }
          |}
          |""".stripMargin,
        "NullableBridge.on",
        Array()
      )
      assert(Shell.Success("got a") == result)
    }
  }
}
