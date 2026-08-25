package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The Java-style-annotated-method hint (`commonSyntaxHint`'s
 * `JavaStyleAnnotatedMethod` case) must resolve through the bilingual
 * `error.parsing.hint.*` bundle in both locales, like every other hint in that
 * match -- see JavaStyleImplementsHintI18nSpec for the same pattern.
 */
class JavaStyleAnnotatedMethodHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.java_style_annotated_method"
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

  it("leaves no unfilled placeholder and no leaked MessageFormat quoting when formatted") {
    val formattedEn = java.text.MessageFormat.format(en.getString(key), "Override")
    val formattedJa = java.text.MessageFormat.format(ja.getString(key), "Override")
    assert(!formattedEn.matches("(?s).*\\{\\d+\\}.*"), s"unfilled placeholder: $formattedEn")
    assert(!formattedJa.matches("(?s).*\\{\\d+\\}.*"), s"unfilled placeholder: $formattedJa")
    assert(!formattedEn.contains("'{'") && !formattedEn.contains("'}'"), s"leaked MessageFormat quoting: $formattedEn")
    assert(!formattedJa.contains("'{'") && !formattedJa.contains("'}'"), s"leaked MessageFormat quoting: $formattedJa")
  }

  it("fires for a Java-style `@Override void method() { ... }` declaration") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  @Override void method() { IO::println("x") }
        |  static def main(args: String[]): Int { return 0 }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The captured annotation name is literal, identical in both bundles, so this
    // assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("@Override"), s"expected the hint to name @Override, got: $msgs")
  }
}
