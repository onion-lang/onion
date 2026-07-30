package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.util.{Locale, ResourceBundle}

/**
 * Regression guard for two `errorMessage_ja.properties` entries that carried untranslated
 * (or half-translated) English prose instead of Japanese text: `error.parsing.syntax_error`
 * (the most common parse-error message of all) and `error.semantic.lValueRequired` (E0028).
 * `LawCheckMessageI18nSpec` only checks that a key *resolves* in both locales, not that the
 * Japanese text is actually Japanese, so this slipped through untested.
 */
class ErrorMessageJapaneseTranslationSpec extends AnyFunSpec with Diagrams {
  private val en = ResourceBundle.getBundle("errorMessage", Locale.ENGLISH)
  private val ja = ResourceBundle.getBundle("errorMessage", Locale.JAPANESE)

  it("translates error.parsing.syntax_error's English tail into Japanese") {
    val text = ja.getString("error.parsing.syntax_error")
    assert(!text.toLowerCase(Locale.ROOT).contains("encountered"))
    assert(!text.toLowerCase(Locale.ROOT).contains("expecting"))
    assert(text != en.getString("error.parsing.syntax_error"))
  }

  it("translates error.semantic.lValueRequired into Japanese") {
    val text = ja.getString("error.semantic.lValueRequired")
    assert(!text.toLowerCase(Locale.ROOT).contains("lvalue"))
    assert(!text.toLowerCase(Locale.ROOT).contains("required"))
    assert(text != en.getString("error.semantic.lValueRequired"))
  }

  it("leaves no unfilled placeholder when formatting the fixed messages") {
    val syntaxError = java.text.MessageFormat.format(
      ja.getString("error.parsing.syntax_error"), "foo", "bar")
    assert(!syntaxError.matches("(?s).*\\{\\d+\\}.*"))
  }
}
