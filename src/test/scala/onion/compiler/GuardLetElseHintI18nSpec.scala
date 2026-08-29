package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The Swift-style `guard let ... else` early-exit hint
 * (`SyntaxHintClassifier`'s `GuardLetElseBinding` case) must resolve through
 * the bilingual `error.parsing.hint.*` bundle in both locales, like every
 * other hint in that match -- see IfLetBindingHintI18nSpec for the same
 * pattern.
 */
class GuardLetElseHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.guard_let_else"
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

  it("fires for a Swift-style `guard let x = ... else { ... }` early exit") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    val opt: String? = "hi"
        |    guard let x = opt else {
        |      return
        |    }
        |    IO::println(x)
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The substituted name/expression are literal source text, identical in
    // both bundles, so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("val x = opt"), s"expected the hint's `val x = opt` example, got: $msgs")
    assert(msgs.contains("if x == null"), s"expected the hint's `if x == null` example, got: $msgs")
  }
}
