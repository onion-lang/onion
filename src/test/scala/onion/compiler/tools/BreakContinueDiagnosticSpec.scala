package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A top-level break/continue followed by another statement must report the real
 * cause (E0048/E0049, break/continue outside a loop), not a misleading syntax
 * error on the next line. The parser used to let break/continue swallow the
 * following line's identifier as a label.
 */
class BreakContinueDiagnosticSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("break/continue outside a loop diagnostics") {
    it("reports E0048 for a top-level break followed by a statement") {
      assert(errorCodes("break\nIO::println(\"end\")\n").contains("E0048"))
    }
    it("reports E0049 for a top-level continue followed by a statement") {
      assert(errorCodes("continue\nIO::println(\"end\")\n").contains("E0049"))
    }
    it("reports E0048 across a blank line too") {
      assert(errorCodes("break\n\nIO::println(\"end\")\n").contains("E0048"))
    }
    it("reports E0048 for a labeled break at top level") {
      assert(errorCodes("break outer\nIO::println(\"end\")\n").contains("E0048"))
    }
  }
}
