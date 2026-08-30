package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A JavaScript/Python-style prefix `yield expr` statement (`ControlFlowSyntaxHints`'s
 * `LeadingYieldStatement` case) must resolve through the bilingual `error.parsing.hint.*`
 * bundle in both locales, like every other hint in that family -- see
 * AwaitStatementHintI18nSpec for the same pattern. Onion has no generator/coroutine
 * `yield`, so the previous generic "missing call parens" fallback (suggesting the
 * nonsensical `yield(...)`) was actively misleading.
 */
class YieldStatementHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.yield_not_supported"
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

  it("fires for a JavaScript/Python-style prefix `yield expr` statement") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    yield 5
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The hint text is identical in both bundles' relevant markers, so this
    // assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("List"), s"expected the hint's `List` suggestion, got: $msgs")
    assert(!msgs.contains("yield(...)"), s"the misleading missing-call-parens fallback should not fire, got: $msgs")
  }
}
