package onion.compiler.tools

import onion.tools.Shell

class MethodChainContinuationSpec extends AbstractShellSpec {
  describe("leading-dot method-chain continuation") {
    it("continues a chain when . starts the next line") {
      val result = shell.run(
        """
          |import { java.lang.StringBuilder }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val sb = new StringBuilder()
          |    sb.append("Hello")
          |      .append(" ")
          |      .append("World")
          |    return sb.toString()
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Hello World") == result)
    }

    it("continues a chain across blank lines before the dot") {
      val result = shell.run(
        """
          |import { java.lang.StringBuilder }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val sb = new StringBuilder()
          |    sb.append("A")
          |
          |      .append("B")
          |    return sb.toString()
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("AB") == result)
    }

    it("continues with the safe-call operator on the next line") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s = "hello"
          |    val r = s
          |      ?.toUpperCase()
          |    return r ?: "none"
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("HELLO") == result)
    }

    it("does NOT join a following [ ] as indexing across a newline") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val foo = ["a", "b", "c"]
          |    [1, 2, 3]
          |    return foo[0]
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("a") == result)
    }

    it("does not regress expression-body methods separated by a blank line") {
      val result = shell.run(
        """
          |class Calc {
          |public:
          |  static def f(): Int = 1
          |
          |  static def g(): Int = 2
          |  static def main(args: String[]): Int = Calc::f() + Calc::g()
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(3) == result)
    }

    it("allows blank lines between top-level local declarations") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val a = 1
          |
          |    val b = 2
          |
          |
          |    val c = 3
          |    return a + b + c
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(6) == result)
    }
  }
}
