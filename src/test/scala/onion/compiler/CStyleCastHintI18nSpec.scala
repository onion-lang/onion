package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The C-style prefix-cast hint (`SyntaxHintClassifier`'s `CStyleCast` case)
 * must resolve through the bilingual `error.parsing.hint.*` bundle in both
 * locales, like every other hint in that match -- see GuardElseHintI18nSpec
 * for the same pattern.
 */
class CStyleCastHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.c_style_cast"
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

  it("fires for a C-style prefix cast `(Type) expr`") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    val o: Object = "hi"
        |    val s: String = (String) o
        |    IO::println(s)
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The substituted type name is literal source text, identical in both
    // bundles, so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("(expr as String)"), s"expected the hint's postfix-`as` example, got: $msgs")
  }
}
