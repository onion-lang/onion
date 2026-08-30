package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The Swift-style bare `guard <condition> else { ... }` early-exit hint
 * (`SyntaxHintClassifier`'s `GuardElseBinding` case) must resolve through
 * the bilingual `error.parsing.hint.*` bundle in both locales, like every
 * other hint in that match -- see GuardLetElseHintI18nSpec for the same
 * pattern with the `let`-binding variant.
 */
class GuardElseHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.guard_else"
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

  it("fires for a Swift-style bare `guard cond else { ... }` early exit") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    val cond: Boolean = false
        |    guard cond else {
        |      return
        |    }
        |    IO::println("ok")
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The substituted condition text is literal source text, identical in
    // both bundles, so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("if !(cond)"), s"expected the hint's `if !(cond)` example, got: $msgs")
    assert(!msgs.contains("guard(cond)"), s"must not fall back to the nonsensical missing-call-parens hint, got: $msgs")
  }
}
