package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * `SyntaxHintClassifier`'s final fallback case (fired when `{` is expected but none of the
 * more specific hints match, e.g. a `while`/`if` head missing its block) returned a
 * hard-coded English string instead of going through the bilingual `error.parsing.hint.*`
 * bundle lookup every other hint in that match uses — so a Japanese-locale diagnostic came
 * out with the hint sentence stuck in English mid-message. Both locale bundles must carry
 * the key, and the Japanese entry must actually be Japanese.
 */
class BlockExpectedHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.block_expected"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  it("resolves in the English bundle") {
    assert(en.getString(key).contains("Hint:"))
  }

  it("resolves in the Japanese bundle with actual Japanese text") {
    val text = ja.getString(key)
    assert(text.contains("ヒント"), s"expected a Japanese hint, got: $text")
    assert(!text.contains("Hint:"), s"hint leaked untranslated English, got: $text")
    assert(text != en.getString(key))
  }

  it("routes the missing-block hint through the bundle, not a hard-coded literal") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): Int {
        |    while true
        |      IO::println("x")
        |    return 0
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The compiler resolves error.parsing.hint.* through the JVM's ambient default locale
    // (see onion.compiler.toolbox.Message), not a fixed bundle, so the expected text must
    // follow the same locale this suite is actually running under.
    val expected = if (java.util.Locale.getDefault.getLanguage == "ja") ja.getString(key) else en.getString(key)
    assert(msgs.contains(expected), s"expected the bundled hint text, got: $msgs")
  }
}
