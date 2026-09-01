package onion.compiler

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * E0005 ("method not found") and E0021 ("constructor not found") read "method
 * applicable for X.Y(Z) is not found." and "constructor applicable for X(Y) is not
 * found." -- missing the leading article, the same defect class already fixed for
 * E0018/E0020/E0027/E0028/E0050 and the CLI `error.command.*` messages. The fixed
 * text reads "a method applicable for ..." / "a constructor applicable for ...".
 *
 * Uses `MessageBundles.english`, which pins `Locale.ROOT` regardless of the JVM's
 * default locale, so this test's assertions on English text hold under both the `en`
 * and `ja` full-suite runs (see `MessageBundles` for why a naive
 * `ResourceBundle.getBundle("errorMessage", Locale.ENGLISH)` would not).
 */
class E0005E0021MissingArticleSpec extends AnyFunSuite with Matchers:
  private def englishMessage(key: String, args: Any*): String =
    MessageBundles.format(MessageBundles.english, key, args*)

  test("error.semantic.methodNotFound (E0005) reads 'a method applicable for ...'"):
    englishMessage("error.semantic.methodNotFound", "Foo", "bar", "Int") shouldBe
      "a method applicable for Foo.bar(Int) is not found. Check spelling and argument types."

  test("error.semantic.constructorNotFound (E0021) reads 'a constructor applicable for ...'"):
    englishMessage("error.semantic.constructorNotFound", "Foo", "Int") shouldBe
      "a constructor applicable for Foo(Int) is not found. Check argument types."

  test("Japanese translations are unaffected (no articles in Japanese)"):
    val ja = MessageBundles.japanese
    MessageBundles.format(ja, "error.semantic.methodNotFound", "Foo", "bar", "Int") should include(
      "メソッドが見つかりません"
    )
    MessageBundles.format(ja, "error.semantic.constructorNotFound", "Foo", "Int") should include(
      "コンストラクタが見つかりません"
    )
