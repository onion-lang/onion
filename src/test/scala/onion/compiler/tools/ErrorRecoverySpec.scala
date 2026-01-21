package onion.compiler.tools

import onion.tools.Shell
import onion.compiler.{Parsing, CompilerConfig, InputSource, CompileError}
import onion.compiler.exceptions.CompilationException
import java.io.{StringReader, Reader}

/**
 * Tests for error recovery and improved error reporting.
 */
class ErrorRecoverySpec extends AbstractShellSpec {

  /**
   * Helper to create an InputSource from a string.
   */
  private def stringSource(code: String, fileName: String = "test.on"): InputSource = {
    new InputSource {
      override def openReader: Reader = new StringReader(code)
      override def name: String = fileName
    }
  }

  describe("Error recovery improvements") {

    it("reports semantic errors for undefined variables") {
      val result = shell.run(
        """
          |class Test1 {
          |public:
          |  static def main(args: String[]): Int {
          |    return undefinedVar1
          |  }
          |}
        """.stripMargin,
        "UndefinedVar.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("handles unclosed braces gracefully") {
      val result = shell.run(
        """
          |class UnclosedBrace {
          |public:
          |  static def main(args: String[]): Int {
          |    if (true) {
          |      return 1
          |    // Missing closing brace
          |  }
          |}
        """.stripMargin,
        "UnclosedBrace.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("handles invalid syntax with missing right-hand side") {
      // Using += without proper expression should fail
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 10
          |    x +=
          |    return x
          |  }
          |}
        """.stripMargin,
        "InvalidSyntax.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("handles empty class body") {
      // Empty class body should be valid
      val result = shell.run(
        """
          |class EmptyClass {
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return 0
          |  }
          |}
        """.stripMargin,
        "EmptyClass.on",
        Array()
      )
      assert(result.isInstanceOf[Shell.Success])
    }

    it("handles valid code with newline statement terminators") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 10
          |    var y: Int = 20
          |    return x + y
          |  }
          |}
        """.stripMargin,
        "ValidCode.on",
        Array()
      )
      // This should succeed because Onion uses newlines as statement terminators
      assert(result.isInstanceOf[Shell.Success])
    }

    it("reports error for duplicate class names") {
      val result = shell.run(
        """
          |class Duplicate {
          |public:
          |  static def helper(): Int { return 1 }
          |}
          |class Duplicate {
          |public:
          |  static def helper(): Int { return 2 }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return 0
          |  }
          |}
        """.stripMargin,
        "DuplicateClass.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("reports type mismatch errors") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: Int = "string"
          |    return x
          |  }
          |}
        """.stripMargin,
        "TypeMismatch.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }

  describe("Parsing class error collection") {

    // Helper to create a basic config
    def defaultConfig: CompilerConfig = CompilerConfig(
      classPath = Seq("."),
      superClass = "java.lang.Object",
      encoding = "UTF-8",
      outputDirectory = ".",
      maxErrorReports = 10
    )

    it("collects parse errors without crashing") {
      val parsing = new Parsing(defaultConfig)
      val sources = Seq(stringSource(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return 0
          |  }
          |}
        """.stripMargin
      ))

      // This should not throw for valid code
      val result = parsing.processBody(sources, null)
      assert(result.nonEmpty)
    }

    it("throws CompilationException for invalid code with error details") {
      val parsing = new Parsing(defaultConfig)
      // Use unbalanced braces which is definitely a syntax error
      val sources = Seq(stringSource(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    if (true) {
          |      return 1
          |    // Missing closing brace here
          |  }
          |}
        """.stripMargin
      ))

      val exception = intercept[CompilationException] {
        parsing.processBody(sources, null)
      }

      // Verify error was collected
      assert(exception.problems.nonEmpty)
      assert(exception.problems.head.isInstanceOf[CompileError])
    }
  }
}
