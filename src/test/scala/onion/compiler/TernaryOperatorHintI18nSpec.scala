package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The ternary-operator hint (`commonSyntaxHint`'s `case "?"`) must resolve through the
 * bilingual `error.parsing.hint.*` bundle in both locales, like every other hint in that
 * match -- see OldForInHintI18nSpec for the motivating regression.
 */
class TernaryOperatorHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.ternary"
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

  it("routes the ternary-operator hint through the bundle, not a hard-coded literal") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): Int {
        |    val x = 5
        |    val y = (x > 3) ? 1 : 0
        |    return y
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    val expected = if (java.util.Locale.getDefault.getLanguage == "ja") ja.getString(key) else en.getString(key)
    assert(msgs.contains(expected), s"expected the bundled hint text, got: $msgs")
  }
}
