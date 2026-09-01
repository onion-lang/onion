package onion.compiler

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * E0016 ("cyclic inheritance") reads "inheritance relations which includes X have
 * cyclicity." -- a subject-verb agreement mismatch: the plural subject "relations"
 * takes "includes" (singular), and should take "include" instead. The fixed text
 * reads "inheritance relations which include X have cyclicity."
 *
 * Uses `MessageBundles.english`, which pins `Locale.ROOT` regardless of the JVM's
 * default locale, so this test's assertions on English text hold under both the `en`
 * and `ja` full-suite runs (see `MessageBundles` for why a naive
 * `ResourceBundle.getBundle("errorMessage", Locale.ENGLISH)` would not).
 */
class E0016CyclicInheritanceGrammarSpec extends AnyFunSuite with Matchers:
  private def englishMessage(key: String, args: Any*): String =
    MessageBundles.format(MessageBundles.english, key, args*)

  test("error.semantic.cyclicInheritance (E0016) reads 'inheritance relations which include ... have cyclicity.'"):
    englishMessage("error.semantic.cyclicInheritance", "A") shouldBe
      "inheritance relations which include A have cyclicity."

  test("Japanese translation is unaffected (no subject-verb agreement in Japanese)"):
    val ja = MessageBundles.japanese
    MessageBundles.format(ja, "error.semantic.cyclicInheritance", "A") should include(
      "循環しています"
    )
