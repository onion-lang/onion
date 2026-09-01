package onion.compiler

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * E0089 ("constructor in a record or enum body") reads "a {0} cannot declare
 * `def this`: ...", where {0} is filled with the literal "record" or "enum" at
 * the two call sites (TypingBodyPass.scala). The hardcoded article "a" is wrong
 * for the enum case: it renders "a enum cannot declare ...", not "an enum
 * cannot declare ...". Same defect class as the recent
 * E0005/E0016/E0018/E0020/E0021/E0023/E0027/E0028/E0050/E0051 article fixes,
 * but this one needs the *correct* article per noun rather than a single fixed
 * one, since both "record" and "enum" flow through the same message key.
 *
 * Uses `MessageBundles.english`, which pins `Locale.ROOT` regardless of the
 * JVM's default locale, so this test's assertions on English text hold under
 * both the `en` and `ja` full-suite runs.
 */
class E0089MissingArticleSpec extends AnyFunSuite with Matchers:
  private def englishMessage(key: String, args: Any*): String =
    MessageBundles.format(MessageBundles.english, key, args*)

  test("error.semantic.constructorInRecordOrEnum (E0089) reads 'a record cannot declare ...' for record"):
    englishMessage("error.semantic.constructorInRecordOrEnum", "record", "R", "a record") shouldBe
      "a record cannot declare `def this`: R already has its canonical constructor, and there is nothing a second one could mean. Use a static factory method instead."

  test("error.semantic.constructorInRecordOrEnum (E0089) reads 'an enum cannot declare ...' for enum"):
    englishMessage("error.semantic.constructorInRecordOrEnum", "enum", "E", "an enum") shouldBe
      "an enum cannot declare `def this`: E already has its canonical constructor, and there is nothing a second one could mean. Use a static factory method instead."

  test("Japanese translation is unaffected (no articles in Japanese)"):
    val ja = MessageBundles.japanese
    MessageBundles.format(ja, "error.semantic.constructorInRecordOrEnum", "record", "R", "a record") should include(
      "record では `def this` を宣言できません"
    )
    MessageBundles.format(ja, "error.semantic.constructorInRecordOrEnum", "enum", "E", "an enum") should include(
      "enum では `def this` を宣言できません"
    )
