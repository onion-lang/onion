package onion.compiler.tools

import onion.tools.Shell

class ThrowsClauseSpec extends AbstractShellSpec {
  describe("throws clause") {
    it("should compile method with throws clause") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def riskyMethod(): Int throws Exception {
          |    return 42;
          |  }
          |  static def main(args: String[]): String {
          |    return "" + riskyMethod();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("should compile method with multiple throws types") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def multiThrows(): Int throws Exception, RuntimeException {
          |    return 100;
          |  }
          |  static def main(args: String[]): String {
          |    return "" + multiThrows();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("100") == result)
    }

    it("should compile interface method with throws clause") {
      val result = shell.run(
        """
          |interface Processor {
          |  def process(): String throws Exception
          |}
          |class SimpleProcessor <: Processor {
          |public:
          |  def process(): String throws Exception {
          |    return "processed";
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p: Processor = new SimpleProcessor();
          |    return p.process();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("processed") == result)
    }

    it("should compile class method calling method with throws clause") {
      val result = shell.run(
        """
          |class Helper {
          |public:
          |  static def riskyOp(): String throws RuntimeException {
          |    return "done";
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return Helper::riskyOp();
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("done") == result)
    }
  }
}
