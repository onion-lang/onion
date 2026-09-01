package onion.compiler

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * E0031 ("type argument arity mismatch") and E0034 ("method type argument arity
 * mismatch") spliced raw counts into fixed-plural text: "type X expects {1} type
 * arguments, but {2} are supplied." -- a single expected/supplied type argument reads
 * "expects 1 type arguments, but 1 are supplied.", wrong in both directions (singular
 * "arguments" and "are" paired with 1). Same defect class as the E0046 wrong-binding-
 * count fix: `SemanticErrorReporter` now supplies pre-pluralized English clauses
 * (`pluralizeCount` for "expects N type argument(s)", and a subject-less "N is/are
 * supplied" verb agreement for the second clause) alongside the raw counts the
 * Japanese template still uses directly (Japanese counts with "個" and has no plural
 * form to agree), so the fixed text reads "expects 1 type argument, but 1 is
 * supplied." / "expects 2 type arguments, but 3 are supplied." as appropriate.
 *
 * Uses `MessageBundles.english`, which pins `Locale.ROOT` regardless of the JVM's
 * default locale, so this test's assertions on English text hold under both the `en`
 * and `ja` full-suite runs (see `MessageBundles` for why a naive
 * `ResourceBundle.getBundle("errorMessage", Locale.ENGLISH)` would not).
 */
class E0031E0034TypeArgumentArityGrammarSpec extends AnyFunSuite with Matchers:
  private def englishMessage(key: String, args: Any*): String =
    MessageBundles.format(MessageBundles.english, key, args*)

  test("error.semantic.typeArgumentArityMismatch (E0031) uses singular 'type argument'/'is supplied' for count 1"):
    englishMessage("error.semantic.typeArgumentArityMismatch", "Box", 1, 1, "1 type argument", "1 is supplied") shouldBe
      "type Box expects 1 type argument, but 1 is supplied."

  test("error.semantic.typeArgumentArityMismatch (E0031) uses plural 'type arguments'/'are supplied' for count > 1"):
    englishMessage("error.semantic.typeArgumentArityMismatch", "Pair", 2, 3, "2 type arguments", "3 are supplied") shouldBe
      "type Pair expects 2 type arguments, but 3 are supplied."

  test("error.semantic.typeArgumentArityMismatch (E0031) mixes singular expected with plural supplied correctly"):
    englishMessage("error.semantic.typeArgumentArityMismatch", "Box", 1, 2, "1 type argument", "2 are supplied") shouldBe
      "type Box expects 1 type argument, but 2 are supplied."

  test("error.semantic.methodTypeArgumentArityMismatch (E0034) uses singular 'type argument'/'is supplied' for count 1"):
    englishMessage(
      "error.semantic.methodTypeArgumentArityMismatch",
      "Test",
      "identity",
      1,
      1,
      "1 type argument",
      "1 is supplied"
    ) shouldBe "method Test.identity expects 1 type argument, but 1 is supplied."

  test("error.semantic.methodTypeArgumentArityMismatch (E0034) uses plural 'type arguments'/'are supplied' for count > 1"):
    englishMessage(
      "error.semantic.methodTypeArgumentArityMismatch",
      "Test",
      "convert",
      2,
      3,
      "2 type arguments",
      "3 are supplied"
    ) shouldBe "method Test.convert expects 2 type arguments, but 3 are supplied."

  test("Japanese translations are unaffected (counts with a counter word, no plural form to agree)"):
    val ja = MessageBundles.japanese
    MessageBundles.format(ja, "error.semantic.typeArgumentArityMismatch", "Box", 1, 1, "unused", "unused") should include(
      "型引数"
    )
    MessageBundles.format(
      ja,
      "error.semantic.methodTypeArgumentArityMismatch",
      "Test",
      "identity",
      1,
      1,
      "unused",
      "unused"
    ) should include("型引数")
