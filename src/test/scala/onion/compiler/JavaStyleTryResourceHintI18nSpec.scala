package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The Java-style-try-resource hint (`commonSyntaxHint`'s `JavaStyleTryResource`
 * case) must resolve through the bilingual `error.parsing.hint.*` bundle in
 * both locales, like every other hint in that match -- see
 * JavaStyleRecordBodyHintI18nSpec for the same pattern.
 */
class JavaStyleTryResourceHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.java_style_try_resource"
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

  it("fires for a Java-style `try (Res r = ...) { ... }` declaration") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Res {
        |public:
        |  def this { }
        |  def close: void { }
        |}
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    try (Res r = new Res()) {
        |      IO::println("using")
        |    }
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The example code shown in the hint is literal, identical in both bundles,
    // so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("val r: Res"), s"expected the hint's `val r: Res` example, got: $msgs")
  }
}
