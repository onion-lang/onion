package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The Java enhanced-for hint (`SyntaxHintClassifier`'s
 * `JavaStyleEnhancedForLoop` case, for `for (Type name : coll) { ... }`) must
 * resolve through the bilingual `error.parsing.hint.*` bundle in both
 * locales, like every other hint in that match -- see GuardElseHintI18nSpec
 * for the same pattern.
 */
class JavaStyleEnhancedForHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.java_style_enhanced_for"
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

  it("fires for a Java enhanced-for loop `for (String s : list) { ... }`") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    val list: List = ["a", "b"]
        |    for (String s : list) {
        |      IO::println(s)
        |    }
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The substituted variable/type/collection text is literal source text,
    // identical in both bundles, so this assertion holds regardless of the
    // JVM's default locale.
    assert(msgs.contains("foreach s: String in list"), s"expected the hint's `foreach` example, got: $msgs")
  }
}
