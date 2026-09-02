package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The Scala-style `case class` declaration hint (`SyntaxHintClassifier`'s
 * `CaseClassDeclaration` case) must resolve through the bilingual
 * `error.parsing.hint.*` bundle in both locales, like every other hint in
 * that match -- see DataClassDeclarationHintI18nSpec for the same pattern.
 */
class CaseClassDeclarationHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.case_class_declaration"
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

  it("fires for a Scala-style `case class` declaration and drops val/var from the record hint") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |case class Point(val x: Int, val y: Int)
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The substituted name and component list are literal source text (with
    // `val`/`var` stripped), identical in both bundles, so this assertion
    // holds regardless of the JVM's default locale.
    assert(msgs.contains("record Point(x: Int, y: Int)"), s"expected the hint's `record Point(x: Int, y: Int)` example, got: $msgs")
  }
}
