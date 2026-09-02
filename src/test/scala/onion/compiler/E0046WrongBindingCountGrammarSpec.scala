package onion.compiler

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * E0046 ("wrong number of bindings in destructuring pattern") reads "Type X has {1}
 * fields, but {2} bindings were specified." with the count spliced in raw -- a number
 * agreement bug: a 1-field/1-binding record renders "has 1 fields" and "1 bindings
 * were specified", both wrong. Same defect class as the E0016 subject-verb agreement
 * fix, but for count/noun/verb agreement instead of a fixed plural subject: the fixed
 * text pre-pluralizes each clause ("has 1 field, but 1 binding was specified." /
 * "has 2 fields, but 3 bindings were specified.").
 *
 * `SemanticErrorReporter` supplies the pre-pluralized English clauses as extra
 * message arguments ({3}/{4}) alongside the raw counts ({1}/{2}) that the Japanese
 * template still uses directly (Japanese counts with "個" and has no plural form to
 * agree), so this test supplies all five positional arguments the same way the real
 * reporter does.
 *
 * Uses `MessageBundles.english`, which pins `Locale.ROOT` regardless of the JVM's
 * default locale, so this test's assertions on English text hold under both the `en`
 * and `ja` full-suite runs (see `MessageBundles` for why a naive
 * `ResourceBundle.getBundle("errorMessage", Locale.ENGLISH)` would not).
 */
class E0046WrongBindingCountGrammarSpec extends AnyFunSuite with Matchers:
  private def englishMessage(key: String, args: Any*): String =
    MessageBundles.format(MessageBundles.english, key, args*)

  test("error.semantic.wrongBindingCount (E0046) uses singular 'field'/'binding was specified' for count 1"):
    englishMessage("error.semantic.wrongBindingCount", "Point", 1, 1, "1 field", "1 binding was specified") shouldBe
      "wrong number of bindings in destructuring pattern. Type Point has 1 field, but 1 binding was specified."

  test("error.semantic.wrongBindingCount (E0046) uses plural 'fields'/'bindings were specified' for count > 1"):
    englishMessage("error.semantic.wrongBindingCount", "Point", 2, 3, "2 fields", "3 bindings were specified") shouldBe
      "wrong number of bindings in destructuring pattern. Type Point has 2 fields, but 3 bindings were specified."

  test("error.semantic.wrongBindingCount (E0046) mixes singular field with plural bindings correctly"):
    englishMessage("error.semantic.wrongBindingCount", "Box", 1, 2, "1 field", "2 bindings were specified") shouldBe
      "wrong number of bindings in destructuring pattern. Type Box has 1 field, but 2 bindings were specified."

  test("Japanese translation is unaffected (counts with a counter word, no plural form to agree)"):
    val ja = MessageBundles.japanese
    MessageBundles.format(ja, "error.semantic.wrongBindingCount", "Point", 1, 1, "unused", "unused") should include(
      "一致しません"
    )
