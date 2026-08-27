package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The trailing-lambda arrow used to be `=>`; it is `->` now, the same as every
 * other arrow in the language (`SyntaxHintClassifier`'s `OldArrowTrailingLambdaHead`
 * case). The functional behavior is covered by TrailingLambdaParenHintSpec, but
 * like every other hint in that match, the message must also resolve through
 * the bilingual `error.parsing.hint.*` bundle in both locales; see
 * OldForInHintI18nSpec for the motivating regression.
 */
class OldTrailingArrowHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.old_trailing_arrow"
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

  it("fires for a trailing lambda still using the old `=>`") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): Int {
        |    val xs = [1, 2, 3].filter { x => x > 1 }
        |    return 0
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The example code shown in the hint is literal, identical in both bundles,
    // so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("{ x -> ... }"), s"expected the hint's `{ x -> ... }` example, got: $msgs")
  }
}
