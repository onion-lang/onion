package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression test: the safe-call operator (`?.`) did not fall through to extension methods
 * when no instance method was found. `s?.shout()` on a user-declared `extension String`
 * and `n?.doubled()` on a user-declared `extension Int` both raised E0005.
 * Both reference-type and primitive-type nullable receivers must work.
 */
class SafeCallExtensionSpec extends AbstractShellSpec {

  private val preamble =
    """
      |extension Int {
      |  def doubled(): Int = self * 2
      |  def square(): Int = self * self
      |}
      |extension String {
      |  def shout(): String = self + "!"
      |  def rep(n: Int): String {
      |    var s: String = ""
      |    var i: Int = 0
      |    while i < n { s = s + self; i++ }
      |    return s
      |  }
      |}
    """.stripMargin

  private def run(body: String): Shell.Result =
    shell.run(preamble + "\n" + body, "SafeCallExt.on", Array())

  describe("safe-call extension methods") {

    it("String? safe-call extension returns value when non-null") {
      assert(Shell.Success("hello!") == run(
        """class T { public: static def main(args: String[]): String {
          |  val s: String? = "hello"; return s?.shout() ?: "FAIL"
          |} }""".stripMargin
      ))
    }

    it("String? safe-call extension returns null (elvis default) when null") {
      assert(Shell.Success("DEFAULT") == run(
        """class T { public: static def main(args: String[]): String {
          |  val s: String? = null; return s?.shout() ?: "DEFAULT"
          |} }""".stripMargin
      ))
    }

    it("Int? safe-call extension returns value when non-null") {
      assert(Shell.Success(10) == run(
        """def maybeInt(): Int? = 5
          |class T { public: static def main(args: String[]): Int {
          |  val n: Int? = maybeInt(); return n?.doubled() ?: -1
          |} }""".stripMargin
      ))
    }

    it("Int? safe-call extension returns elvis default when null") {
      assert(Shell.Success(-1) == run(
        """def nullInt(): Int? = null
          |class T { public: static def main(args: String[]): Int {
          |  val n: Int? = nullInt(); return n?.doubled() ?: -1
          |} }""".stripMargin
      ))
    }

    it("Int? safe-call extension with square") {
      assert(Shell.Success(49) == run(
        """def seven(): Int? = 7
          |class T { public: static def main(args: String[]): Int {
          |  val n: Int? = seven(); return n?.square() ?: 0
          |} }""".stripMargin
      ))
    }

    it("String? safe-call extension with arguments works") {
      assert(Shell.Success("abcabc") == run(
        """class T { public: static def main(args: String[]): String {
          |  val s: String? = "abc"; return s?.rep(2) ?: "FAIL"
          |} }""".stripMargin
      ))
    }

    it("safe-call extension result chains to toString") {
      assert(Shell.Success("10") == run(
        """def maybeInt(): Int? = 5
          |class T { public: static def main(args: String[]): String {
          |  val n: Int? = maybeInt(); return n?.doubled()?.toString() ?: "FAIL"
          |} }""".stripMargin
      ))
    }
  }
}
