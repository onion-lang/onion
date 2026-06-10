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
    it("substitutes extends-clause type arguments for inherited members") {
      val result = shell.run(
        """
          |class Box[T extends Object] {
          |  val item: T
          |public:
          |  def this(item: T) { this.item = item }
          |  def get(): T { return this.item }
          |}
          |
          |class StringBox : Box[String] {
          |public:
          |  def this(s: String) : (s) {}
          |  def shout(): String { return this.get().toUpperCase() }
          |
          |  static def main(args: String[]): String {
          |    return new StringBox("hi").shout()
          |  }
          |}
          |""".stripMargin,
        "InheritedSubstitution.on",
        Array()
      )
      assert(Shell.Success("HI") == result)
    }
  }
}
