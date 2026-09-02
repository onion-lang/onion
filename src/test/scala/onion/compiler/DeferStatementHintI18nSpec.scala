package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A Go-style prefix `defer expr` statement (`ControlFlowSyntaxHints`'s
 * `LeadingDeferStatement` case) must resolve through the bilingual `error.parsing.hint.*`
 * bundle in both locales, like every other hint in that family -- see
 * AwaitStatementHintI18nSpec for the same pattern. Onion has no `defer` statement, so
 * the previous generic "missing call parens" fallback (suggesting the nonsensical
 * `defer(...)`) was actively misleading.
 */
class DeferStatementHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.defer_not_supported"
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

  it("fires for a Go-style prefix `defer expr` statement") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    val f = "x"
        |    defer f.length
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The substituted expression is literal source text, identical in both
    // bundles, so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("finally { f.length }"), s"expected the hint's `try`/`finally` example, got: $msgs")
    assert(!msgs.contains("defer(f.length)"), s"the misleading missing-call-parens fallback should not fire, got: $msgs")
  }
}
