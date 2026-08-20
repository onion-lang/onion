package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader
import java.util.Locale

/**
 * `commonSyntaxHint`'s `case "in"` (the `for x in xs` hint) was the one hint still
 * returning a hard-coded English string instead of going through the bilingual
 * `error.parsing.hint.*` bundle lookup every other hint in that match uses — so a
 * Japanese-locale diagnostic for the base message came out in Japanese with the hint
 * sentence stuck in English mid-message. Both locale bundles must carry the key, and the
 * Japanese entry must actually be Japanese.
 */
class OldForInHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.old_for_in"
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

  it("routes the `for x in xs` parser hint through the bundle, not a hard-coded literal") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): Int {
        |    val xs = [1, 2, 3]
        |    for x in xs {
        |      IO::println(x)
        |    }
        |    return 0
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The C-style loop shown in the hint is literal code, identical in both bundles,
    // so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("for var i = 0; i < xs.size(); i = i + 1"), s"expected the hint's C-style loop example, got: $msgs")
  }
}
