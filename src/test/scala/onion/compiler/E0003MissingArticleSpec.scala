package onion.compiler

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * E0003 ("class not found") reads "type X is not found. Check spelling or add
 * import." -- missing the indefinite article before the singular countable noun
 * "import". Same defect class as the E0005/E0018/E0020/E0021/E0023/E0027/E0028/
 * E0050/E0051/E0089/E0016 fixes: the fixed text reads "... or add an import."
 *
 * Uses `MessageBundles.english`, which pins `Locale.ROOT` regardless of the JVM's
 * default locale, so this test's assertions on English text hold under both the `en`
 * and `ja` full-suite runs (see `MessageBundles` for why a naive
 * `ResourceBundle.getBundle("errorMessage", Locale.ENGLISH)` would not).
 */
class E0003MissingArticleSpec extends AnyFunSuite with Matchers:
  private def englishMessage(key: String, args: Any*): String =
    MessageBundles.format(MessageBundles.english, key, args*)

  test("error.semantic.classNotFound (E0003) reads '... Check spelling or add an import.'"):
    englishMessage("error.semantic.classNotFound", "Foo") shouldBe
      "type Foo is not found. Check spelling or add an import."

  test("Japanese translation is unaffected (no articles in Japanese)"):
    val ja = MessageBundles.japanese
    MessageBundles.format(ja, "error.semantic.classNotFound", "Foo") should include(
      "見つかりません"
    )
