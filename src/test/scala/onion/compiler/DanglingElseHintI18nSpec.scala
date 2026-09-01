package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The dangling-`else`-with-no-matching-`if` hint (`SyntaxHintClassifier`'s
 * bare `"else"` case) must resolve through the bilingual
 * `error.parsing.hint.*` bundle in both locales, like every other hint in
 * that match -- see TernaryHintI18nSpec for the same pattern.
 */
class DanglingElseHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.dangling_else"
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

  it("fires for an `else` with no matching preceding `if`") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |val x = 5
        |else {
        |  IO::println(x)
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // Unlike TernaryHintI18nSpec's literal code example, this hint's explanation is
    // ordinary prose, and the Japanese bundle translates it in full -- so an English
    // phrase like "only `if` takes an `else`" is not actually present under a
    // Japanese-locale JVM. The backticked tokens themselves are the one part left
    // untranslated in both bundles, so assert on those instead.
    assert(msgs.contains("`else`"), s"expected the hint to mention `else`, got: $msgs")
    assert(msgs.contains("`if`"), s"expected the hint to mention `if`, got: $msgs")
  }
}
