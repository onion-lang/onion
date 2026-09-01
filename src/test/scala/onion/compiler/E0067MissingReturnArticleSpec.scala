package onion.compiler

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * E0067 ("missing return") reads "method {0} may reach the end of its body
 * without returning a {1}.", where {1} is filled with the method's declared
 * return type name. The hardcoded article "a" is wrong whenever that type name
 * starts with a vowel sound: `def bar(): Int { ... }` renders "without
 * returning a Int." instead of "without returning an Int.", and likewise for
 * `Object`, `Array`, etc. Same defect class as the recent E0003/E0089 missing/
 * wrong-article fixes: `SemanticErrorReporter` now derives the article from the
 * substituted type name (`indefiniteArticled`, already used for E0089) instead
 * of hardcoding one in the English message bundle; the Japanese message (no
 * articles) is unaffected.
 *
 * Uses `MessageBundles.english`, which pins `Locale.ROOT` regardless of the
 * JVM's default locale, so this test's assertions on English text hold under
 * both the `en` and `ja` full-suite runs.
 */
class E0067MissingReturnArticleSpec extends AnyFunSuite with Matchers:
  private def englishMessage(key: String, args: Any*): String =
    MessageBundles.format(MessageBundles.english, key, args*)

  test("error.semantic.missingReturn (E0067) reads 'an Int' for a vowel-initial return type"):
    englishMessage("error.semantic.missingReturn", "bar", "Int", "an Int") shouldBe
      "method bar may reach the end of its body without returning an Int. Every path must return a value (or throw); use an explicit `return`, or the `= expr` body form."

  test("error.semantic.missingReturn (E0067) reads 'a String' for a consonant-initial return type"):
    englishMessage("error.semantic.missingReturn", "bar", "String", "a String") shouldBe
      "method bar may reach the end of its body without returning a String. Every path must return a value (or throw); use an explicit `return`, or the `= expr` body form."

  test("Japanese translation is unaffected (no articles in Japanese)"):
    val ja = MessageBundles.japanese
    MessageBundles.format(ja, "error.semantic.missingReturn", "bar", "Int", "unused") should include(
      "本体の末尾に到達する可能性があります"
    )
