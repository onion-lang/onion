package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * `SemanticErrorReporter.reportConstructorNotFound`'s "Available constructors:" list was a
 * hard-coded English literal, unlike every other suggestion appended to a not-found
 * diagnostic (`error.suggestion.candidates`, `suggestion.didYouMean`, ...), which all go
 * through the bilingual `errorMessage*.properties` bundle. Under a Japanese-locale JVM the
 * base "constructor ... is not found" message came out in Japanese with the constructor
 * list header stuck in English.
 */
class ConstructorNotFoundHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.suggestion.availableConstructors"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  private val src =
    """
      |class Foo {
      |public:
      |  def this(x: Int) { }
      |}
      |class Main {
      |public:
      |  static def main(args: String[]): Int {
      |    val f = new Foo("bad")
      |    return 0
      |  }
      |}
      |""".stripMargin

  private def compileErrors(): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  it("resolves in the English bundle") {
    assert(en.getString(key).nonEmpty)
  }

  it("resolves in the Japanese bundle with actual Japanese text, distinct from English") {
    val text = ja.getString(key)
    assert(!text.toLowerCase(java.util.Locale.ROOT).contains("available"), s"leaked untranslated English, got: $text")
    assert(text != en.getString(key))
  }

  it("routes the available-constructors list through the bundle, not a hard-coded literal") {
    val msgs = compileErrors()
    // The constructor signature itself is literal code, identical in both bundles, so this
    // assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("Foo(Int)"), s"expected the available constructor signature, got: $msgs")
    assert(!msgs.contains("Available constructors:"), s"the header leaked hard-coded English, got: $msgs")
  }
}
