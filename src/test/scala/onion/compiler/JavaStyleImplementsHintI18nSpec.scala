package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The Java-style-`implements` hint (`commonSyntaxHint`'s `JavaStyleImplements`
 * case) must resolve through the bilingual `error.parsing.hint.*` bundle in
 * both locales, like every other hint in that match -- see
 * JavaStyleRecordBodyHintI18nSpec for the same pattern.
 */
class JavaStyleImplementsHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.java_style_implements"
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

  it("fires for a Java-style `class Person implements Greeter { ... }` declaration") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |interface Greeter { def greet(): String }
        |class Person implements Greeter {
        |public:
        |  def this {}
        |  def greet(): String = "hi"
        |}
        |class Test {
        |public:
        |  static def main(args: String[]): Int { return 0 }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The example code shown in the hint is literal, identical in both bundles,
    // so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("class Person conforms Greeter"), s"expected the hint's `class Person conforms Greeter` example, got: $msgs")
  }
}
