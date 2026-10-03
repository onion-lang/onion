package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Regression test for issue #1966: a `#{...}` interpolation body nested deeper than
 * `OnionLexer.MaxInterpBraceDepth` is balanced (every brace has a matching close), but the
 * lexer's own tokenizing could not resolve the nesting and fell back to treating the `#{` as
 * literal text, truncating the surrounding STRING token at the first unescaped quote inside
 * the body and reporting the (wrongly truncated) result as unclosed. It must instead report
 * a dedicated "too deep" diagnostic, and a nesting depth within the limit must still compile.
 */
class InterpolationTooDeepDiagnosticSpec extends AnyFunSpec {

  private def newConfig: CompilerConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def compile(code: String) =
    new OnionCompiler(newConfig).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )

  private def nested(depth: Int): String = {
    val open = "if true { ".repeat(depth) + "\"deep\""
    val close = " } else { \"x\" }".repeat(depth)
    open + close
  }

  describe("a #{...} interpolation nested deeper than the supported depth") {
    it("is reported with a dedicated 'too deep' message, not as unclosed") {
      val result = compile(
        s"""
           |IO::println("5: #{${nested(5)}}")
           |""".stripMargin
      )
      assert(result.hasErrors)
      val errors = result.allErrors
      assert(
        errors.exists(e => !e.message.contains("unclosed") && !e.message.contains("閉じ")),
        s"expected a dedicated too-deep message, got: ${errors.map(_.message)}"
      )
      assert(errors.exists(e => e.location != null && e.location.line == 2),
        s"expected the error anchored at line 2, got: ${errors.map(e => (e.location, e.message))}")
    }

    it("still compiles at the supported depth (no false positive)") {
      val result = compile(
        s"""
           |IO::println("4: #{${nested(4)}}")
           |""".stripMargin
      )
      assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
    }
  }
}
