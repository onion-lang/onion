package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The foreach-parens hint (`commonSyntaxHint`'s `ForeachParenMistake` case) must
 * resolve through the bilingual `error.parsing.hint.*` bundle in both locales,
 * like every other hint in that match -- see OldForInHintI18nSpec for the
 * motivating regression.
 */
class ForeachParensHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.foreach_parens"
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

  it("fires for a Java/Kotlin/JS-style `foreach (x in xs)` clause") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): Int {
        |    val xs = [1, 2, 3]
        |    foreach (x in xs) {
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
    // The example code shown in the hint is literal, identical in both bundles,
    // so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("foreach x: Type in xs"), s"expected the hint's foreach example, got: $msgs")
  }
}
