package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The TypeScript-style optional field/parameter hint (`SyntaxHintClassifier`'s
 * `"?:"` case) must resolve through the bilingual `error.parsing.hint.*` bundle
 * in both locales, like every other hint in that match -- see
 * NullishCoalescingHintI18nSpec for the same pattern.
 */
class TypeScriptStyleOptionalAnnotationHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.typescript_style_optional_annotation"
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

  it("fires for a TypeScript-style optional field `name?: Type`") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Foo {
        |  var name?: String
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
    // The example code in the hint is literal, identical in both bundles, so this
    // assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("name: String?"), s"expected the hint's fixed-up example, got: $msgs")
  }

  it("fires for a TypeScript-style optional parameter `name?: Type`") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Foo {
        |public:
        |  def bar(count?: Int): Int {
        |    return 0
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(msgs.contains("count: Int?"), s"expected the hint's fixed-up example, got: $msgs")
  }
}
