package onion.compiler.tools

import onion.tools.Shell

class DidYouMeanSpec extends AbstractShellSpec {
  describe("Error messages with suggestions") {
    describe("for method names") {
      it("reports error for typo in method name") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return "hello".tUpperCase();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        // Method not found error should be reported
        // "did you mean: toUpperCase" suggestion will be shown in stderr
        assert(result.isInstanceOf[Shell.Failure])
      }

      it("reports error for method name with wrong case") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return "hello".Length();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        // "did you mean: length" suggestion will be shown
        assert(result.isInstanceOf[Shell.Failure])
      }
    }

    describe("for field/getter names") {
      it("reports error for typo in record field name") {
        val result = shell.run(
          """
            |record Person(name: String, age: Int);
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val p = new Person("Alice", 30);
            |    return p.nme();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        // "did you mean: name" suggestion will be shown
        assert(result.isInstanceOf[Shell.Failure])
      }
    }

    describe("for variable names") {
      it("reports error for typo in variable name") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val userName = "Alice";
            |    return usrName;
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        // "did you mean: userName" suggestion will be shown
        assert(result.isInstanceOf[Shell.Failure])
      }
    }

    describe("for class names") {
      it("reports error for typo in class name") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val list = new ArrayLst[String]();
            |    return "ok";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        // Class not found error will be shown
        // "did you mean: ArrayList" suggestion may be shown if in import scope
        assert(result.isInstanceOf[Shell.Failure])
      }
    }
  }
}
