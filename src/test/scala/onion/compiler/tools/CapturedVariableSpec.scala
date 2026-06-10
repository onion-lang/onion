package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression tests for issue #128: boxed captured variables are stored as
 * Object in the box; reads must checkcast back to the declared type or the
 * JVM verifier rejects the enclosing method (Bad return type / Bad type on
 * operand stack).
 */
class CapturedVariableSpec extends AbstractShellSpec {

  describe("Captured variables") {
    it("returns a mutated captured var from the enclosing method") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    var seen = ""
          |    val f = () -> { seen = "captured"; }
          |    f()
          |    return seen
          |  }
          |}
          |""".stripMargin,
        "CapturedVarReturn.on",
        Array()
      )
      assert(Shell.Success("captured") == result)
    }

    it("calls methods on a captured val inside a closure in a static method") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val sb = new StringBuffer()
          |    val f = () -> { sb.append("a").append("b"); }
          |    f()
          |    f()
          |    return sb.toString()
          |  }
          |}
          |""".stripMargin,
        "CapturedValMethodCall.on",
        Array()
      )
      assert(Shell.Success("abab") == result)
    }

    it("passes a mutated captured var as a typed argument") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def shout(s: String): String { return s + "!" }
          |  static def main(args: String[]): String {
          |    var word = "hi"
          |    val f = () -> { word = "yo"; }
          |    f()
          |    return shout(word)
          |  }
          |}
          |""".stripMargin,
        "CapturedVarAsArgument.on",
        Array()
      )
      assert(Shell.Success("yo!") == result)
    }
  }
}
