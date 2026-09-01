package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A Python/Ruby-style `not` boolean negation in an `if`/`while`/`else if`
 * condition (`SyntaxHintClassifier`'s `NotOperatorCondition` case) must
 * resolve through the bilingual `error.parsing.hint.*` bundle in both
 * locales, like every other hint in that family -- see
 * NullishCoalescingHintI18nSpec for the same pattern with a zero-argument
 * hint. `not` isn't a keyword in Onion, so `if not ready { ... }` previously
 * fell through to the generic "a block is expected here" fallback, which
 * pointed nowhere near the actual mistake. Onion's negation is `!`.
 */
class NotOperatorHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.not_operator"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  it("resolves the key in the English bundle") {
    assert(en.getString(key).contains("Hint:"))
  }

  it("resolves the key in the Japanese bundle with actual Japanese text") {
    val text = ja.getString(key)
    assert(text.contains("ヒント"), s"expected a Japanese hint, got: $text")
    assert(!text.contains("Hint:"), s"hint leaked untranslated English, got: $text")
    assert(text != en.getString(key))
  }

  it("fires for a Python/Ruby-style `not` boolean negation in an `if` condition") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    val ready: Boolean = false
        |    if not ready {
        |      IO::println("not ready")
        |    }
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The hint's rewrite and mistake are both literal code examples,
    // identical in both bundles, so this assertion holds regardless of the
    // JVM's default locale.
    assert(msgs.contains("if !condition"), s"expected the hint's `!` rewrite, got: $msgs")
    assert(msgs.contains("if not condition"), s"expected the hint to echo the mistake shape, got: $msgs")
  }
}
