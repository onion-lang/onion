package onion.compiler.tools

import onion.tools.Shell

class SafeCallExtensionMethodSpec extends AbstractShellSpec {
  describe("Safe call (?.) on extension methods") {

    it("calls extension method on nullable String and returns result when non-null") {
      val result = shell.run(
        """
          |extension String {
          |  def shout(): String = self.toUpperCase() + "!"
          |}
          |
          |class Main {
          |public:
          |  static def getStr(flag: Boolean): String? {
          |    if flag { return "hello" }
          |    return null
          |  }
          |
          |  static def main(args: String[]): String {
          |    val opt: String? = getStr(true)
          |    return "" + opt?.shout()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("HELLO!") == result)
    }

    it("calls extension method on nullable String and returns null when null") {
      val result = shell.run(
        """
          |extension String {
          |  def shout(): String = self.toUpperCase() + "!"
          |}
          |
          |class Main {
          |public:
          |  static def getStr(flag: Boolean): String? {
          |    if flag { return "hello" }
          |    return null
          |  }
          |
          |  static def main(args: String[]): String {
          |    val opt: String? = getStr(false)
          |    return "" + opt?.shout()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("calls extension method on nullable Int and returns result when non-null") {
      val result = shell.run(
        """
          |extension Int {
          |  def double(): Int = self * 2
          |}
          |
          |class Main {
          |public:
          |  static def getOpt(flag: Boolean): Int? {
          |    if flag { return 21 }
          |    return null
          |  }
          |
          |  static def main(args: String[]): String {
          |    val opt: Int? = getOpt(true)
          |    return "" + opt?.double()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("calls extension method on nullable Int and returns null when null") {
      val result = shell.run(
        """
          |extension Int {
          |  def double(): Int = self * 2
          |}
          |
          |class Main {
          |public:
          |  static def getOpt(flag: Boolean): Int? {
          |    if flag { return 21 }
          |    return null
          |  }
          |
          |  static def main(args: String[]): String {
          |    val opt: Int? = getOpt(false)
          |    return "" + opt?.double()
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("calls a zero-arg extension method accessed property-style (no parens) on a nullable String and returns result when non-null") {
      val result = shell.run(
        """
          |extension String {
          |  def shout: String = self.toUpperCase() + "!"
          |}
          |
          |class Main {
          |public:
          |  static def getStr(flag: Boolean): String? {
          |    if flag { return "hello" }
          |    return null
          |  }
          |
          |  static def main(args: String[]): String {
          |    val opt: String? = getStr(true)
          |    return "" + opt?.shout
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("HELLO!") == result)
    }

    it("calls a zero-arg extension method accessed property-style (no parens) on a nullable String and returns null when null") {
      val result = shell.run(
        """
          |extension String {
          |  def shout: String = self.toUpperCase() + "!"
          |}
          |
          |class Main {
          |public:
          |  static def getStr(flag: Boolean): String? {
          |    if flag { return "hello" }
          |    return null
          |  }
          |
          |  static def main(args: String[]): String {
          |    val opt: String? = getStr(false)
          |    return "" + opt?.shout
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
    }
  }
}
