package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The `using`-resource-statement hint (`SyntaxHintClassifier`'s
 * `UsingResourceStatement` case) must resolve through the bilingual
 * `error.parsing.hint.*` bundle in both locales, like every other hint in
 * that match -- see JsStyleLetHintI18nSpec for the same pattern.
 */
class UsingResourceStatementHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.using_resource_statement"
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

  it("fires for a C#/Python-style `using r = new Res() { ... }` statement") {
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
        |    using r = new Res() {
        |      IO::println("using")
        |    }
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The substituted variable name and expression are literal source text,
    // identical in both bundles, so this assertion holds regardless of the
    // JVM's default locale.
    assert(msgs.contains("try (val r = new Res())"), s"expected the hint's `try (val r = new Res())` example, got: $msgs")
  }
}
