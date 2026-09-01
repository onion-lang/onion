package onion.compiler

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * E0051 ("return type is required") reads "return type is required for method X." --
 * missing the leading article, the same defect class already fixed for
 * E0005/E0018/E0020/E0021/E0023/E0027/E0028/E0050 and the CLI `error.command.*`
 * messages. The fixed text reads "a return type is required for method X."
 *
 * Uses `MessageBundles.english`, which pins `Locale.ROOT` regardless of the JVM's
 * default locale, so this test's assertions on English text hold under both the `en`
 * and `ja` full-suite runs (see `MessageBundles` for why a naive
 * `ResourceBundle.getBundle("errorMessage", Locale.ENGLISH)` would not).
 */
class E0051MissingArticleSpec extends AnyFunSuite with Matchers:
  private def englishMessage(key: String, args: Any*): String =
    MessageBundles.format(MessageBundles.english, key, args*)

  test("error.semantic.returnTypeRequired (E0051) reads 'a return type is required for method ...'"):
    englishMessage("error.semantic.returnTypeRequired", "f") shouldBe
      "a return type is required for method f."

  test("Japanese translation is unaffected (no articles in Japanese)"):
    val ja = MessageBundles.japanese
    MessageBundles.format(ja, "error.semantic.returnTypeRequired", "f") should include(
      "戻り型を明示してください"
    )
