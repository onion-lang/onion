package onion.compiler.tools

import onion.tools.Shell

class DefaultStaticImportSpec extends AbstractShellSpec {
  describe("Default static imports") {
    it("allows IO static calls without qualification") {
      val result = shell.run(
        """
          |class StaticImportSample {
          |public:
          |  static def main(args: String[]): String {
          |    println("hello")
          |    print("world")
          |    return "ok"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("resolves a varargs IO method (format) without qualification") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String = format("a=%s b=%d", "hi", 42)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("a=hi b=42") == result)
    }

    it("calls bare printf with a primitive vararg") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    printf("n=%d", 7)
          |    return "ok"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("calls bare Math methods (sqrt, max) without qualification") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Double = sqrt(max(9, 16) as Double)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(4.0) == result)
    }
  }
}
