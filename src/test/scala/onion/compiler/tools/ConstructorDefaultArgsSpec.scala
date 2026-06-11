package onion.compiler.tools

import onion.tools.Shell

/**
 * Constructor default parameter values: omitted positional arguments fill
 * from declared defaults, alone or combined with named arguments.
 */
class ConstructorDefaultArgsSpec extends AbstractShellSpec {

  describe("Constructor default arguments") {
    it("fills all defaults for new C()") {
      val result = shell.run(
        """
          |class Conf {
          |  val host: String
          |  val port: Int
          |public:
          |  def this(host: String = "localhost", port: Int = 8080) {
          |    this.host = host
          |    this.port = port
          |  }
          |  def show(): String { return this.host + ":" + this.port }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return new Conf().show()
          |  }
          |}
          |""".stripMargin,
        "CtorAllDefaults.on",
        Array()
      )
      assert(Shell.Success("localhost:8080") == result)
    }

    it("combines named arguments with defaults") {
      val result = shell.run(
        """
          |class Conf {
          |  val host: String
          |  val port: Int
          |public:
          |  def this(host: String = "localhost", port: Int = 8080) {
          |    this.host = host
          |    this.port = port
          |  }
          |  def show(): String { return this.host + ":" + this.port }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return new Conf(port = 9090).show() + "/" + new Conf("h").show()
          |  }
          |}
          |""".stripMargin,
        "CtorNamedDefaults.on",
        Array()
      )
      assert(Shell.Success("localhost:9090/h:8080") == result)
    }

    it("still rejects missing required arguments") {
      val result = shell.run(
        """
          |class C {
          |  val a: Int
          |  val b: Int
          |public:
          |  def this(a: Int, b: Int = 2) { this.a = a; this.b = b }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val c = new C()
          |    return "no"
          |  }
          |}
          |""".stripMargin,
        "CtorMissingRequired.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
