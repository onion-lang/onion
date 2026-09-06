package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The annotation-before-modifier hint (`SyntaxHintClassifier`'s
 * `AnnotationBeforeModifierMethod` case) must resolve through the bilingual
 * `error.parsing.hint.*` bundle in both locales, like every other hint in that
 * match -- see JavaStyleAnnotatedMethodHintI18nSpec for the same pattern.
 */
class AnnotationBeforeModifierHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.annotation_before_modifier"
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
    val formattedEn = java.text.MessageFormat.format(en.getString(key), "TailRecursive", "static")
    val formattedJa = java.text.MessageFormat.format(ja.getString(key), "TailRecursive", "static")
    assert(!formattedEn.matches("(?s).*\\{\\d+\\}.*"), s"unfilled placeholder: $formattedEn")
    assert(!formattedJa.matches("(?s).*\\{\\d+\\}.*"), s"unfilled placeholder: $formattedJa")
    assert(!formattedEn.contains("'{'") && !formattedEn.contains("'}'"), s"leaked MessageFormat quoting: $formattedEn")
    assert(!formattedJa.contains("'{'") && !formattedJa.contains("'}'"), s"leaked MessageFormat quoting: $formattedJa")
  }

  it("fires for a `@TailRecursive static def foo() { ... }` declaration (annotation before the modifier)") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |private:
        |  @TailRecursive
        |  static def isEven(n: Int): Boolean { if n == 0 { return true }; return isOdd(n - 1) }
        |  @TailRecursive
        |  static def isOdd(n: Int): Boolean { if n == 0 { return false }; return isEven(n - 1) }
        |public:
        |  static def main(args: String[]): Int { return 0 }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The captured annotation name and modifier are literal, identical in both
    // bundles, so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("@TailRecursive"), s"expected the hint to name @TailRecursive, got: $msgs")
    assert(msgs.contains("static"), s"expected the hint to name the modifier, got: $msgs")
  }

  it("does not fire when the annotation is already correctly placed after the modifier") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static @TailRecursive def isEven(n: Int): Boolean { return true }
        |  static def main(args: String[]): Int { return 0 }
        |}
        |""".stripMargin
    val result = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on")))
    assert(result.isInstanceOf[CompilationOutcome.Success], s"expected success, got: $result")
  }
}
