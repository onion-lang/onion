package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The Python-style `raise` statement hints (`ControlFlowSyntaxHints`'s
 * `LeadingRaiseConstructorCall`/`LeadingRaiseStatement` cases) must resolve
 * through the bilingual `error.parsing.hint.*` bundle in both locales, like
 * every other hint in that family -- see PhpStyleElseifHintI18nSpec for the
 * same pattern.
 */
class PythonStyleRaiseHintI18nSpec extends AnyFunSpec with Diagrams {
  private val constructKey = "error.parsing.hint.python_style_raise_construct"
  private val bareKey = "error.parsing.hint.python_style_raise"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  it("resolves the constructor-call key in the English bundle") {
    assert(en.getString(constructKey).contains("Hint:"))
  }

  it("resolves the constructor-call key in the Japanese bundle with actual Japanese text") {
    val text = ja.getString(constructKey)
    assert(text.contains("ヒント"), s"expected a Japanese hint, got: $text")
    assert(!text.contains("Hint:"), s"hint leaked untranslated English, got: $text")
    assert(text != en.getString(constructKey))
  }

  it("resolves the bare-raise key in the English bundle") {
    assert(en.getString(bareKey).contains("Hint:"))
  }

  it("resolves the bare-raise key in the Japanese bundle with actual Japanese text") {
    val text = ja.getString(bareKey)
    assert(text.contains("ヒント"), s"expected a Japanese hint, got: $text")
    assert(!text.contains("Hint:"), s"hint leaked untranslated English, got: $text")
    assert(text != en.getString(bareKey))
  }

  it("fires for a Python-style `raise Exception(...)` statement") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    raise Exception("boom")
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The substituted callee/args are literal source text, identical in both
    // bundles, so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("throw new Exception(\"boom\")"), s"expected the hint's `throw new` example, got: $msgs")
  }

  it("fires for a Python-style bare `raise e` re-raise") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    try {
        |      IO::println("x")
        |    } catch e: Exception {
        |      raise e
        |    }
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(msgs.contains("throw e"), s"expected the hint's `throw e` example, got: $msgs")
  }
}
