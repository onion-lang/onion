package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The Java-style-import-alias hint (`commonSyntaxHint`'s `JavaStyleImportAlias` case)
 * must resolve through the bilingual `error.parsing.hint.*` bundle in both locales,
 * like every other hint in that match -- see OldForInHintI18nSpec for the motivating
 * regression.
 */
class JavaStyleImportAliasHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.java_style_import_alias"
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

  it("fires for a Java-style import alias written before the target") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |import {
        |  Foo = java.lang.Long;
        |}
        |
        |class Main {
        |public:
        |  static def main(args: String[]): Int {
        |    return 0
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The captured identifiers are literal source text, identical in both bundles,
    // so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("java.lang.Long as Foo"), s"expected the hint's `java.lang.Long as Foo` rewrite, got: $msgs")
  }
}
