package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The reserved-word-as-identifier hint (`SyntaxHintClassifier`'s `ReservedWords`
 * case) must resolve through the bilingual `error.parsing.hint.*` bundle in
 * both locales, like every other hint in that match -- see
 * JavaStyleImplementsHintI18nSpec for the same pattern.
 */
class ReservedWordIdentifierHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.reserved_word_identifier"
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

  it("fires for `val class = 5`, naming the reserved word") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |val class = 5
        |IO::println(class)
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The backtick-escaped word is literal, identical in both bundles, so this
    // assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("`class`"), s"expected the hint's backtick-escaped `class`, got: $msgs")
  }
}
