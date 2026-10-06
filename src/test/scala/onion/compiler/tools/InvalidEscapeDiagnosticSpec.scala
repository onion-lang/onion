package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.compiler.toolbox.Message
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A string literal whose closing quote is present but whose content has an escape the lexer
 * does not accept (`"\d+"`) must not be reported as unterminated. Messages are resolved via
 * `Message(...)` so the spec holds in both the en and ja runs.
 */
class InvalidEscapeDiagnosticSpec extends AnyFunSpec {

  private def compile(code: String) =
    new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 10)).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )

  private def messages(code: String): Seq[String] = {
    val r = compile(code)
    assert(r.hasErrors)
    r.allErrors.map(_.message)
  }

  describe("a string literal with an unsupported escape") {
    it("is reported as an invalid escape at the escape's column") {
      val ms = messages("val s = \"\\d+\"\nIO::println(s)\n")
      assert(ms.exists(_.contains(Message("error.parsing.invalid_escape", 10))), ms)
      assert(!ms.exists(_.contains(Message("error.parsing.unterminated_string"))), ms)
    }

    it("reports the first bad escape in the middle of a string") {
      val ms = messages("val s = \"abc\\def\"\n")
      assert(ms.exists(_.contains(Message("error.parsing.invalid_escape", 13))), ms)
    }
  }

  describe("a genuinely unterminated string") {
    it("is still reported as unterminated, even when it holds a bad escape") {
      for (code <- Seq("val s = \"abc\n", "val s = \"a\\d\n")) {
        val ms = messages(code)
        assert(ms.exists(_.contains(Message("error.parsing.unterminated_string"))), ms)
      }
    }
  }
}
