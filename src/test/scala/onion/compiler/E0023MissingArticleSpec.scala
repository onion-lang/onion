package onion.compiler

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * E0023 ("interface required") reads "interface required, but type X is used." --
 * missing both the leading article and the linking verb, the same defect class
 * already fixed for E0005/E0018/E0020/E0021/E0027/E0028/E0050 and the CLI
 * `error.command.*` messages. The fixed text reads "an interface is required, but
 * type X is used."
 *
 * Uses `MessageBundles.english`, which pins `Locale.ROOT` regardless of the JVM's
 * default locale, so this test's assertions on English text hold under both the `en`
 * and `ja` full-suite runs (see `MessageBundles` for why a naive
 * `ResourceBundle.getBundle("errorMessage", Locale.ENGLISH)` would not).
 */
class E0023MissingArticleSpec extends AnyFunSuite with Matchers:
  private def englishMessage(key: String, args: Any*): String =
    MessageBundles.format(MessageBundles.english, key, args*)

  test("error.semantic.interfaceRequired (E0023) reads 'an interface is required, but type ... is used.'"):
    englishMessage("error.semantic.interfaceRequired", "Foo") shouldBe
      "an interface is required, but type Foo is used."

  test("Japanese translation is unaffected (no articles in Japanese)"):
    val ja = MessageBundles.japanese
    MessageBundles.format(ja, "error.semantic.interfaceRequired", "Foo") should include(
      "インタフェースが要求されています"
    )
