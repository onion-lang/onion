package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

/**
 * The `catch (e: Exception) { }` hint (`commonSyntaxHint`'s parenthesized-catch
 * case in Parsing.scala) must resolve in both locale bundles, with the Japanese
 * entry actually written in Japanese rather than falling back to English.
 */
class CatchParensHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.catch_parens"
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
}
