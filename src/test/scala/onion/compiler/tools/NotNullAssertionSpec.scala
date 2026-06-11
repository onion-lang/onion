package onion.compiler.tools

import onion.tools.Shell

/**
 * Non-null assertion expr!!: strips one level of nullability, throwing
 * NullPointerException when the value is actually null.
 */
class NotNullAssertionSpec extends AbstractShellSpec {

  describe("Non-null assertion !!") {
    it("unwraps a nullable value and allows direct dereference") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val maybe: String? = "hello"
          |    return "" + maybe!!.length()
          |  }
          |}
          |""".stripMargin,
        "BangDeref.on",
        Array()
      )
      assert(Shell.Success("5") == result)
    }

    it("throws NullPointerException when the value is null") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def len(s: String?): Int { return s!!.length() }
          |  static def main(args: String[]): String {
          |    try {
          |      return "" + Test::len(null)
          |    } catch e: NullPointerException {
          |      return "NPE"
          |    }
          |  }
          |}
          |""".stripMargin,
        "BangThrows.on",
        Array()
      )
      assert(Shell.Success("NPE") == result)
    }

    it("unwraps a nullable type variable inside a generic body") {
      val result = shell.run(
        """
          |class Box[T] {
          |  val item: T
          |public:
          |  def this(item: T) { this.item = item }
          |  def force(): String { return this.item!!.toString() }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return new Box[String?]("forced").force()
          |  }
          |}
          |""".stripMargin,
        "BangGeneric.on",
        Array()
      )
      assert(Shell.Success("forced") == result)
    }

    it("assigns the unwrapped type") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val maybe: String? = "typed"
          |    val sure: String = maybe!!
          |    return sure
          |  }
          |}
          |""".stripMargin,
        "BangAssign.on",
        Array()
      )
      assert(Shell.Success("typed") == result)
    }
  }
}
