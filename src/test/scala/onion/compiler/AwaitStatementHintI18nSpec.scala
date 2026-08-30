package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A JavaScript/Python-style prefix `await expr` statement (`ControlFlowSyntaxHints`'s
 * `LeadingAwaitStatement` case) must resolve through the bilingual `error.parsing.hint.*`
 * bundle in both locales, like every other hint in that family -- see
 * PythonStyleRaiseHintI18nSpec for the same pattern. Onion's `Future` is awaited
 * postfix (`f.await()`), so the previous generic "missing call parens" fallback
 * (suggesting the nonsensical `await(...)`) was actively misleading.
 */
class AwaitStatementHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.await_not_supported"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  it("resolves the key in the English bundle") {
    assert(en.getString(key).contains("Hint:"))
  }

  it("resolves the key in the Japanese bundle with actual Japanese text") {
    val text = ja.getString(key)
    assert(text.contains("ヒント"), s"expected a Japanese hint, got: $text")
    assert(!text.contains("Hint:"), s"hint leaked untranslated English, got: $text")
    assert(text != en.getString(key))
  }

  it("fires for a JavaScript/Python-style prefix `await expr` statement") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    await foo()
        |  }
        |  static def foo(): Int = 1
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The substituted expression is literal source text, identical in both
    // bundles, so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("foo().await()"), s"expected the hint's postfix `.await()` example, got: $msgs")
  }
}
