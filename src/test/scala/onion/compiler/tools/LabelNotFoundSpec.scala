package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * break/continue with a label that isn't bound to any enclosing labeled loop
 * must report E0058 (LABEL_NOT_FOUND), not just fail generically.
 */
class LabelNotFoundSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("undefined label diagnostics") {
    it("reports E0058 for break with an unbound label") {
      assert(errorCodes(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): void {
          |    foreach i: Int in 0..3 {
          |      break nosuch
          |    }
          |  }
          |}
          |""".stripMargin
      ).contains("E0058"))
    }

    it("reports E0058 for continue with an unbound label") {
      assert(errorCodes(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): void {
          |    foreach i: Int in 0..3 {
          |      continue nosuch
          |    }
          |  }
          |}
          |""".stripMargin
      ).contains("E0058"))
    }

    it("does not report E0058 when the label is correctly bound") {
      assert(!errorCodes(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): void {
          |    outer: foreach i: Int in 0..3 {
          |      break outer
          |    }
          |  }
          |}
          |""".stripMargin
      ).contains("E0058"))
    }
  }
}
