package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The parenthesized-trailing-lambda hint (`commonSyntaxHint`'s `ParenthesizedTrailingLambdaHead`
 * case) must resolve through the bilingual `error.parsing.hint.*` bundle in both locales, like
 * every other hint in that match -- see OldForInHintI18nSpec for the motivating regression.
 * Unlike the other hints in this family, this one is a `MessageFormat` template (it interpolates
 * the captured parameter list), so the routing check formats the bundle entry with the same
 * argument the compiler would rather than comparing raw bundle text.
 */
class TrailingLambdaParenHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.trailing_lambda_parens"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  it("resolves in the English bundle") {
    assert(en.getString(key).contains("Hint:"))
  }

  it("resolves in the Japanese bundle with actual Japanese text") {
    val text = ja.getString(key)
    assert(text.contains("ヒント"), s"expected a Japanese hint, got: $text")
    assert(!text.contains("Hint:"), s"hint leaked untranslated English, got: $text")
    assert(text != en.getString(key))
  }

  it("routes the trailing-lambda-parens hint through the bundle, not a hard-coded literal") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): Int {
        |    val xs = [1, 2, 3]
        |    val r = xs.map { (x) -> x * 2 }
        |    return 0
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    val bundle = if (java.util.Locale.getDefault.getLanguage == "ja") ja else en
    val expected = MessageBundles.format(bundle, key, "x")
    assert(msgs.contains(expected), s"expected the bundled hint text, got: $msgs")
  }
}
