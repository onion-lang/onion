package onion.compiler

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * The CLI-level `error.command.*` messages (raised by `CompilerFrontend`/`ScriptRunner`
 * for a malformed command line -- a missing option value, an unrecognized flag, a bad
 * `-maxErrorReport`/`-encoding` value) were missing articles, the same defect already
 * fixed for E0018/E0020/E0027: "requires argument.", "is invalid argument.", "is not
 * valid encoding name.", and (worst) "is required to natural number." -- which is not
 * just missing an article but grammatically broken (no verb linking "required" to
 * "natural number"). None of these have an E-code or a doc entry (they are CLI usage
 * errors, not compiler diagnostics), so the only regression net is this test.
 *
 * Uses `MessageBundles.english`, which pins `Locale.ROOT` regardless of the JVM's
 * default locale, so this test's assertions on English text hold under both the `en`
 * and `ja` full-suite runs (see `MessageBundles` for why a naive
 * `ResourceBundle.getBundle("errorMessage", Locale.ENGLISH)` would not).
 */
class CommandErrorMessageGrammarSpec extends AnyFunSuite with Matchers:
  private def englishMessage(key: String, arg: String): String =
    MessageBundles.format(MessageBundles.english, key, arg)

  test("error.command.noArgument reads 'requires an argument'"):
    englishMessage("error.command.noArgument", "-classpath") shouldBe
      "-classpath requires an argument."

  test("error.command.invalidArgument reads 'is an invalid argument'"):
    englishMessage("error.command.invalidArgument", "-bogus") shouldBe
      "-bogus is an invalid argument."

  test("error.command.requireNaturalNumber reads 'is required to be a natural number'"):
    englishMessage("error.command.requireNaturalNumber", "-maxErrorReport") shouldBe
      "value of -maxErrorReport is required to be a natural number."

  test("error.command.invalidEncoding reads 'is not a valid encoding name'"):
    englishMessage("error.command.invalidEncoding", "-encoding") shouldBe
      "value of -encoding is not a valid encoding name."

  test("Japanese translations are unaffected (no articles in Japanese)"):
    val ja = MessageBundles.japanese
    MessageBundles.format(ja, "error.command.noArgument", "-classpath") should include("は引数を要求します")
    MessageBundles.format(ja, "error.command.invalidArgument", "-bogus") should include("は不正な引数です")
    MessageBundles.format(ja, "error.command.requireNaturalNumber", "-maxErrorReport") should include(
      "正の整数でなければなりません"
    )
    MessageBundles.format(ja, "error.command.invalidEncoding", "-encoding") should include(
      "不正なエンコーディング名です"
    )
