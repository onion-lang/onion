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

    it("calls a zero-arg extension method accessed property-style (no parens) on a nullable Int and returns result when non-null") {
      val result = shell.run(
        """
          |extension Int {
          |  def tripled: Int = self * 3
          |}
          |
          |class Main {
          |public:
          |  static def getOpt(flag: Boolean): Int? {
          |    if flag { return 7 }
          |    return null
          |  }
          |
          |  static def main(args: String[]): String {
          |    val opt: Int? = getOpt(true)
          |    return "" + opt?.tripled
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("21") == result)
    }

    it("calls a zero-arg extension method accessed property-style (no parens) on a nullable Int and returns null when null") {
      val result = shell.run(
        """
          |extension Int {
          |  def tripled: Int = self * 3
          |}
          |
          |class Main {
          |public:
          |  static def getOpt(flag: Boolean): Int? {
          |    if flag { return 7 }
          |    return null
          |  }
          |
          |  static def main(args: String[]): String {
          |    val opt: Int? = getOpt(false)
          |    return "" + opt?.tripled
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("calls a zero-arg extension method accessed property-style (no parens) on a nullable Double and returns result when non-null") {
      val result = shell.run(
        """
          |extension Double {
          |  def halved: Double = self / 2.0
          |}
          |
          |class Main {
          |public:
          |  static def getOpt(flag: Boolean): Double? {
          |    if flag { return 9.0 }
          |    return null
          |  }
          |
          |  static def main(args: String[]): String {
          |    val opt: Double? = getOpt(true)
          |    return "" + opt?.halved
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("4.5") == result)
    }

    it("calls a zero-arg extension method accessed property-style (no parens) on a nullable Double and returns null when null") {
      val result = shell.run(
        """
          |extension Double {
          |  def halved: Double = self / 2.0
          |}
          |
          |class Main {
          |public:
          |  static def getOpt(flag: Boolean): Double? {
          |    if flag { return 9.0 }
          |    return null
          |  }
          |
          |  static def main(args: String[]): String {
          |    val opt: Double? = getOpt(false)
          |    return "" + opt?.halved
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("calls a zero-arg extension method accessed property-style (no parens) on a nullable Boolean and returns result when non-null") {
      val result = shell.run(
        """
          |extension Boolean {
          |  def negated: Boolean = !self
          |}
          |
          |class Main {
          |public:
          |  static def getOpt(flag: Boolean): Boolean? {
          |    if flag { return true }
          |    return null
          |  }
          |
          |  static def main(args: String[]): String {
          |    val opt: Boolean? = getOpt(true)
          |    return "" + opt?.negated
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("false") == result)
    }

    it("calls a zero-arg extension method accessed property-style (no parens) on a nullable Boolean and returns null when null") {
      val result = shell.run(
        """
          |extension Boolean {
          |  def negated: Boolean = !self
          |}
          |
          |class Main {
          |public:
          |  static def getOpt(flag: Boolean): Boolean? {
          |    if flag { return true }
          |    return null
          |  }
          |
          |  static def main(args: String[]): String {
          |    val opt: Boolean? = getOpt(false)
          |    return "" + opt?.negated
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("calls a zero-arg extension method accessed property-style (no parens) on a nullable Long and returns result when non-null") {
      val result = shell.run(
        """
          |extension Long {
          |  def doubled: Long = self * 2L
          |}
          |
          |class Main {
          |public:
          |  static def getOpt(flag: Boolean): Long? {
          |    if flag { return 21L }
          |    return null
          |  }
          |
          |  static def main(args: String[]): String {
          |    val opt: Long? = getOpt(true)
          |    return "" + opt?.doubled
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("calls a zero-arg extension method accessed property-style (no parens) on a nullable Long and returns null when null") {
      val result = shell.run(
        """
          |extension Long {
          |  def doubled: Long = self * 2L
          |}
          |
          |class Main {
          |public:
          |  static def getOpt(flag: Boolean): Long? {
          |    if flag { return 21L }
          |    return null
          |  }
          |
          |  static def main(args: String[]): String {
          |    val opt: Long? = getOpt(false)
          |    return "" + opt?.doubled
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("calls a one-arg extension method via safe call (parens) on a nullable Int and returns result when non-null") {
      val result = shell.run(
        """
          |extension Int {
          |  def plusN(n: Int): Int = self + n
          |}
          |
          |class Main {
          |public:
          |  static def getOpt(flag: Boolean): Int? {
          |    if flag { return 10 }
          |    return null
          |  }
          |
          |  static def main(args: String[]): String {
          |    val opt: Int? = getOpt(true)
          |    return "" + opt?.plusN(5)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("15") == result)
    }

    it("calls a one-arg extension method via safe call (parens) on a nullable Int and returns null when null") {
      val result = shell.run(
        """
          |extension Int {
          |  def plusN(n: Int): Int = self + n
          |}
          |
          |class Main {
          |public:
          |  static def getOpt(flag: Boolean): Int? {
          |    if flag { return 10 }
          |    return null
          |  }
          |
          |  static def main(args: String[]): String {
          |    val opt: Int? = getOpt(false)
          |    return "" + opt?.plusN(5)
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("chains a record-accessor safe call into a property-style extension safe-nav on a nullable Int component") {
      val result = shell.run(
        """
          |record Box(v: Int?)
          |
          |extension Int {
          |  def half: Int? {
          |    if self % 2 == 0 { return self / 2 }
          |    return null
          |  }
          |}
          |
          |class Main {
          |public:
          |  static def getBox(flag: Boolean): Box? {
          |    if flag { return new Box(8) }
          |    return null
          |  }
          |
          |  static def main(args: String[]): String {
          |    val present: Box? = getBox(true)
          |    val absent: Box? = getBox(false)
          |    return "" + present?.v()?.half + "," + absent?.v()?.half
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("4,null") == result)
    }
  }
}
