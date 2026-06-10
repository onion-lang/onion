package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for static fields: the section-modifier leak (a 'static' on one
 * member leaked onto all following members, turning constructors static
 * and crashing codegen), static field assignment via ::, and compile-time
 * access checks on static field reads/writes.
 */
class StaticFieldSpec extends AbstractShellSpec {

  describe("Static fields") {
    it("reads and writes a public static field via ::") {
      val result = shell.run(
        """
          |class Config {
          |public:
          |  static var name: String = "onion"
          |  def this {}
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    Config::name = Config::name + "!"
          |    return Config::name
          |  }
          |}
          |""".stripMargin,
        "StaticFieldReadWrite.on",
        Array()
      )
      assert(Shell.Success("onion!") == result)
    }

    it("does not leak 'static' from a field onto following members") {
      val result = shell.run(
        """
          |class Counter {
          |  static var count: Int
          |public:
          |  static def bump(): void { Counter::count = Counter::count + 1 }
          |  static def get(): Int { return Counter::count }
          |  def this {}
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    Counter::bump()
          |    Counter::bump()
          |    return Counter::get()
          |  }
          |}
          |""".stripMargin,
        "ModifierNoLeak.on",
        Array()
      )
      assert(Shell.Success(2) == result)
    }

    it("rejects reading a private static field from outside at compile time") {
      val result = shell.run(
        """
          |class C {
          |  static var secret: Int = 1
          |public:
          |  def this {}
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return C::secret
          |  }
          |}
          |""".stripMargin,
        "PrivateStaticRead.on",
        Array()
      )
      assert(result == Shell.Failure(-1))
    }

    it("rejects assigning to a val static field") {
      val result = shell.run(
        """
          |class C {
          |public:
          |  static val limit: Int = 10
          |  def this {}
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    C::limit = 20
          |    return C::limit
          |  }
          |}
          |""".stripMargin,
        "AssignToStaticVal.on",
        Array()
      )
      assert(result == Shell.Failure(-1))
    }
    it("rejects calling a private method from outside at compile time") {
      val result = shell.run(
        """
          |class C {
          |  def secret(): Int { return 42 }
          |public:
          |  def reveal(): Int { return this.secret() }
          |  def this {}
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return new C().secret()
          |  }
          |}
          |""".stripMargin,
        "PrivateMethodCall.on",
        Array()
      )
      assert(result == Shell.Failure(-1))
    }
  }
}
