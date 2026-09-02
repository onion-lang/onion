package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The JS/TS-style for-of hint (`SyntaxHintClassifier`'s `JsStyleForOfLoop`
 * case, for `for (const x of coll) { ... }`) must resolve through the
 * bilingual `error.parsing.hint.*` bundle in both locales, like every other
 * hint in that match -- see JavaStyleEnhancedForHintI18nSpec for the same
 * pattern.
 */
class JsStyleForOfHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.js_style_for_of"
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

  private def compileFailureMessages(forOfClause: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      s"""
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    val arr: List = ["a", "b"]
        |    $forOfClause {
        |      IO::println(x)
        |    }
        |  }
        |}
        |""".stripMargin
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  // The substituted variable/collection text is literal source text, identical
  // in both bundles, so these assertions hold regardless of the JVM's default
  // locale.
  it("fires for a JS-style `for (const x of arr) { ... }` loop") {
    val msgs = compileFailureMessages("for (const x of arr)")
    assert(msgs.contains("foreach x: Type in arr"), s"expected the hint's `foreach` example, got: $msgs")
  }

  it("fires for a `for (let x of arr) { ... }` loop") {
    val msgs = compileFailureMessages("for (let x of arr)")
    assert(msgs.contains("foreach x: Type in arr"), s"expected the hint's `foreach` example, got: $msgs")
  }

  it("fires for a `for (x of arr) { ... }` loop with no declarator keyword") {
    val msgs = compileFailureMessages("for (x of arr)")
    assert(msgs.contains("foreach x: Type in arr"), s"expected the hint's `foreach` example, got: $msgs")
  }
}
