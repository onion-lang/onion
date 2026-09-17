package onion.compiler.tools

import onion.tools.Shell

/**
 * Safe-call extension methods on nullable receivers (`?.` on T?).
 * Covers the fix for the bug where `x?.double()` on `Int?` compiled to
 * bytecode that NPE'd on the null path (castCall wrapped SafeCallStatic in
 * AsInstanceOf(_, BasicType.INT), so intValue() ran on the null result).
 */
class SafeExtensionMethodOnNullableSpec extends AbstractShellSpec {
  describe("safe call on a nullable primitive with a user extension method") {
    it("returns null when the Int? receiver is null") {
      assert(Shell.Success("null") == shell.run(
        """
          |extension Int {
          |  def double(): Int = self * 2
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val y: Int? = null
          |    return "" + y?.double()
          |  }
          |}
          |""".stripMargin, "None", Array()))
    }
    it("applies the extension when the Int? receiver is non-null") {
      assert(Shell.Success("10") == shell.run(
        """
          |extension Int {
          |  def double(): Int = self * 2
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val x: Int? = 5
          |    return "" + x?.double()
          |  }
          |}
          |""".stripMargin, "None", Array()))
    }
    it("works for Long? receiver") {
      assert(Shell.Success("null:21") == shell.run(
        """
          |extension Long {
          |  def triple(): Long = self * 3L
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val a: Long? = null
          |    val b: Long? = 7L
          |    return "" + a?.triple() + ":" + b?.triple()
          |  }
          |}
          |""".stripMargin, "None", Array()))
    }
  }

  describe("safe call on a nullable reference with a user extension method") {
    it("returns null (string 'null') when the String? receiver is null") {
      assert(Shell.Success("null") == shell.run(
        """
          |extension String {
          |  def shout(): String = self.toUpperCase() + "!"
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val s: String? = null
          |    return "" + s?.shout()
          |  }
          |}
          |""".stripMargin, "None", Array()))
    }
    it("applies the extension when the String? receiver is non-null") {
      assert(Shell.Success("HI!") == shell.run(
        """
          |extension String {
          |  def shout(): String = self.toUpperCase() + "!"
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val s: String? = "hi"
          |    return s?.shout() ?: "ERROR"
          |  }
          |}
          |""".stripMargin, "None", Array()))
    }
  }

  describe("safe extension call chained with elvis") {
    it("gives the fallback when Int? is null") {
      assert(Shell.Success("0") == shell.run(
        """
          |extension Int {
          |  def double(): Int = self * 2
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val y: Int? = null
          |    return "" + (y?.double() ?: 0)
          |  }
          |}
          |""".stripMargin, "None", Array()))
    }
  }
}
