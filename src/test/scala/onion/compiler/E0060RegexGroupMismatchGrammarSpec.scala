package onion.compiler

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * E0060 ("regex capture group / binding count mismatch") read "the regex pattern has
 * {0} capture group(s) but {1} binding(s) were given." with the raw counts spliced
 * directly into a literal "(s)" suffix instead of real pluralization -- same defect
 * class as the E0031/E0034/E0046 count-agreement fixes, just with a different tell:
 * "(s)" never resolves to a plural or singular form, and the trailing verb ("were
 * given") stayed fixed-plural regardless of count, so a single group/binding read
 * "has 1 capture group(s) but 1 binding(s) were given" -- wrong in both directions.
 *
 * `SemanticErrorReporter` now supplies pre-pluralized English clauses
 * (`pluralizeCount` for the group-count clause, `pluralizeCountVerb` for the
 * binding-count-plus-verb clause) alongside the raw counts the Japanese template
 * still uses directly (Japanese counts with "個" and has no plural form to agree),
 * so the fixed text reads "has 1 capture group but 1 binding was given." / "has 2
 * capture groups but 3 bindings were given." as appropriate.
 *
 * Uses `MessageBundles.english`, which pins `Locale.ROOT` regardless of the JVM's
 * default locale, so this test's assertions on English text hold under both the `en`
 * and `ja` full-suite runs (see `MessageBundles` for why a naive
 * `ResourceBundle.getBundle("errorMessage", Locale.ENGLISH)` would not).
 */
class E0060RegexGroupMismatchGrammarSpec extends AnyFunSuite with Matchers:
  private def englishMessage(key: String, args: Any*): String =
    MessageBundles.format(MessageBundles.english, key, args*)

  test("error.semantic.regexGroupMismatch (E0060) uses singular 'capture group'/'binding was given' for count 1"):
    englishMessage("error.semantic.regexGroupMismatch", "1", "1", "1 capture group", "1 binding was given") shouldBe
      "the regex pattern has 1 capture group but 1 binding was given."

  test("error.semantic.regexGroupMismatch (E0060) uses plural 'capture groups'/'bindings were given' for count > 1"):
    englishMessage("error.semantic.regexGroupMismatch", "2", "3", "2 capture groups", "3 bindings were given") shouldBe
      "the regex pattern has 2 capture groups but 3 bindings were given."

  test("error.semantic.regexGroupMismatch (E0060) mixes singular groups with plural bindings correctly"):
    englishMessage("error.semantic.regexGroupMismatch", "2", "1", "2 capture groups", "1 binding was given") shouldBe
      "the regex pattern has 2 capture groups but 1 binding was given."

  test("Japanese translation is unaffected (counts with a counter word, no plural form to agree)"):
    val ja = MessageBundles.japanese
    MessageBundles.format(ja, "error.semantic.regexGroupMismatch", "1", "1", "unused", "unused") should include(
      "個"
    )
