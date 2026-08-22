package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The C-style-for-loop hint (`commonSyntaxHint`'s `CStyleForLoop` case) must resolve
 * through the bilingual `error.parsing.hint.*` bundle in both locales, like every other
 * hint in that match -- see OldForInHintI18nSpec for the motivating regression.
 */
class CStyleForLoopHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.c_style_for"
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

  it("fires for a C-style parenthesized for loop") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): Int {
        |    for (int i = 0; i < 10; i++) {
        |      IO::println(i)
        |    }
        |    return 0
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The example loop shown in the hint is literal code, identical in both bundles,
    // so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("for var i: Int = 0; i < 10; i++"), s"expected the hint's example loop, got: $msgs")
  }
}
