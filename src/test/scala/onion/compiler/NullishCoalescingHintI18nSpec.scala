package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The `??` nullish-coalescing hint (`SyntaxHintClassifier`'s `"?"` case,
 * ahead of the plain ternary fallback) must resolve through the bilingual
 * `error.parsing.hint.*` bundle in both locales, like every other hint in
 * that match -- see TernaryHintI18nSpec for the same pattern.
 */
class NullishCoalescingHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.nullish_coalescing"
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

  it("fires for a JS/TS/Kotlin-style `a ?? b` nullish-coalescing expression") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    val a: Int? = null
        |    val b: Int = a ?? 5
        |    IO::println(b)
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The hint text is identical in both bundles' example code, so this
    // assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("a ?: b"), s"expected the hint's Elvis-operator example, got: $msgs")
  }
}
