package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The Kotlin-style `companion object { ... }` declaration hint (`SyntaxHintClassifier`'s
 * `CompanionObjectDeclaration` case) must resolve through the bilingual
 * `error.parsing.hint.*` bundle in both locales, like every other hint in
 * that match -- see ObjectDeclarationHintI18nSpec for the same pattern.
 */
class CompanionObjectDeclarationHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.companion_object_declaration"
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

  it("fires for a Kotlin-style `companion object` declaration inside a class body") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Foo {
        |  companion object {
        |    static def create(): Foo = new Foo()
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(msgs.contains("companion object"), s"expected the hint to mention `companion object`, got: $msgs")
    assert(msgs.contains("static"), s"expected the hint to mention `static`, got: $msgs")
  }

  it("fires for a named Kotlin-style `companion object Name` declaration") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Foo {
        |  companion object Factory {
        |    static def create(): Foo = new Foo()
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(msgs.contains("companion object"), s"expected the hint to mention `companion object`, got: $msgs")
  }
}
