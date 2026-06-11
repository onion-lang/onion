package onion.compiler.tools

import onion.tools.Shell

/**
 * Interface default methods: a body in an interface method declaration
 * compiles to a JVM default method. Implementing classes inherit it,
 * may override it, and dispatch stays virtual through the interface type.
 */
class InterfaceDefaultMethodSpec extends AbstractShellSpec {

  describe("Interface default methods") {
    it("inherits the default implementation") {
      val result = shell.run(
        """
          |interface Greeter {
          |  def name(): String
          |  def greet(): String { return "Hello, " + this.name() }
          |}
          |class K <: Greeter {
          |public:
          |  def this {}
          |  def name(): String { return "kota" }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return new K().greet()
          |  }
          |}
          |""".stripMargin,
        "DefaultInherit.on",
        Array()
      )
      assert(Shell.Success("Hello, kota") == result)
    }

    it("supports expression-bodied default methods") {
      val result = shell.run(
        """
          |interface Greeter {
          |  def name(): String
          |  def shout(): String = "HEY " + this.name()
          |}
          |class K <: Greeter {
          |public:
          |  def this {}
          |  def name(): String { return "kota" }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return new K().shout()
          |  }
          |}
          |""".stripMargin,
        "DefaultExprBody.on",
        Array()
      )
      assert(Shell.Success("HEY kota") == result)
    }

    it("allows overriding and keeps dispatch virtual through the interface") {
      val result = shell.run(
        """
          |interface Greeter {
          |  def name(): String
          |  def greet(): String { return "Hello, " + this.name() }
          |}
          |class Loud <: Greeter {
          |public:
          |  def this {}
          |  def name(): String { return "loud" }
          |  def greet(): String { return "YO " + this.name() }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val g: Greeter = new Loud()
          |    return g.greet()
          |  }
          |}
          |""".stripMargin,
        "DefaultOverride.on",
        Array()
      )
      assert(Shell.Success("YO loud") == result)
    }

    it("does not require implementing classes to define default methods") {
      val result = shell.run(
        """
          |interface WithDefault {
          |  def base(): Int
          |  def doubled(): Int { return this.base() * 2 }
          |}
          |class Impl <: WithDefault {
          |public:
          |  def this {}
          |  def base(): Int { return 21 }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + new Impl().doubled()
          |  }
          |}
          |""".stripMargin,
        "DefaultNotRequired.on",
        Array()
      )
      assert(Shell.Success("42") == result)
    }
  }
}
