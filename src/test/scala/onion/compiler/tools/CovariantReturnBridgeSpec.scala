package onion.compiler.tools

import onion.tools.Shell

/**
 * An override that narrows a reference return type (`name(): String` for an
 * inherited `name(): Object`) needs a bridge method with the inherited
 * signature, as javac emits. Without one, a call through the supertype threw
 * AbstractMethodError (#2386).
 */
class CovariantReturnBridgeSpec extends AbstractShellSpec {
  describe("a covariant return override") {
    it("dispatches through an interface") {
      val result = shell.run(
        """
          |interface Named { def name(): Object }
          |class Q conforms Named {
          |public:
          |  def this {}
          |  def name(): String = "q"
          |  static def main(args: String[]): Object {
          |    val n: Named = new Q()
          |    return n.name()
          |  }
          |}
          |""".stripMargin,
        "CovariantInterface.on",
        Array()
      )
      assert(Shell.Success("q") == result)
    }

    it("dispatches through an interface for a record accessor") {
      val result = shell.run(
        """
          |interface Named { def name(): Object }
          |record P(name: String) conforms Named
          |class Main {
          |public:
          |  static def main(args: String[]): Object {
          |    val n: Named = new P("p")
          |    return n.name()
          |  }
          |}
          |""".stripMargin,
        "CovariantRecord.on",
        Array()
      )
      assert(Shell.Success("p") == result)
    }

    it("dispatches through a superclass, and through an interface the superclass conforms to") {
      val result = shell.run(
        """
          |interface Text { def text(): CharSequence }
          |class Base conforms Text {
          |public:
          |  def this {}
          |  def text(): CharSequence = "base"
          |  def num(): Number = 1
          |}
          |class Sub extends Base {
          |public:
          |  def this {}
          |  override def text(): String = "sub"
          |  override def num(): Integer = 2
          |  static def main(args: String[]): String {
          |    val b: Base = new Sub()
          |    val t: Text = new Sub()
          |    return b.text().toString() + t.text().toString() + b.num()
          |  }
          |}
          |""".stripMargin,
        "CovariantSuper.on",
        Array()
      )
      assert(Shell.Success("subsub2") == result)
    }

    it("leaves an Int overload beside an inherited Integer method alone") {
      val result = shell.run(
        """
          |class Base {
          |public:
          |  def this {}
          |  def foo(x: Integer): String = "boxed"
          |}
          |class Sub extends Base {
          |public:
          |  def this {}
          |  def foo(x: Int): String = "prim"
          |  static def main(args: String[]): String {
          |    val b: Base = new Sub()
          |    val i: Integer = 3
          |    return b.foo(i)
          |  }
          |}
          |""".stripMargin,
        "OverloadNotBridged.on",
        Array()
      )
      assert(Shell.Success("boxed") == result)
    }

    it("still calls the narrower method directly") {
      val result = shell.run(
        """
          |interface Named { def name(): Object }
          |class Q conforms Named {
          |public:
          |  def this {}
          |  def name(): String = "q"
          |  static def main(args: String[]): Int {
          |    return new Q().name().length()
          |  }
          |}
          |""".stripMargin,
        "CovariantDirect.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }

    it("dispatches an interface default method that narrows the return type") {
      val result = shell.run(
        """
          |interface Named { def name(): Object }
          |interface SubNamed conforms Named { def name(): String = "default" }
          |class C conforms SubNamed { public: def this {} }
          |class Main {
          |public:
          |  static def main(args: String[]): Object {
          |    val n: Named = new C()
          |    return n.name()
          |  }
          |}
          |""".stripMargin,
        "CovariantInterfaceDefault.on",
        Array()
      )
      assert(Shell.Success("default") == result)
    }

    it("still lets a class override the narrowed default method") {
      val result = shell.run(
        """
          |interface Named { def name(): Object }
          |interface SubNamed conforms Named { def name(): String = "default" }
          |class C conforms SubNamed {
          |public:
          |  def this {}
          |  def name(): String = "own"
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Object {
          |    val n: Named = new C()
          |    return n.name()
          |  }
          |}
          |""".stripMargin,
        "CovariantInterfaceOverride.on",
        Array()
      )
      assert(Shell.Success("own") == result)
    }
  }
}
