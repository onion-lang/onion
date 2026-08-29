package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The Swift/Rust-style `if let`/`while let` optional-binding hint
 * (`SyntaxHintClassifier`'s `IfWhileLetBinding` case) must resolve through the
 * bilingual `error.parsing.hint.*` bundle in both locales, like every other
 * hint in that match -- see RustStyleMutDeclarationHintI18nSpec for the same
 * pattern.
 */
class IfLetBindingHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.if_let_binding"
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

  it("fires for a Swift-style `if let x = ...` optional binding") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    val opt: String? = "hi"
        |    if let x = opt {
        |      IO::println(x)
        |    }
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The substituted keyword/name/expression are literal source text, identical
    // in both bundles, so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("var x = opt"), s"expected the hint's `var x = opt` example, got: $msgs")
    assert(msgs.contains("if x != null"), s"expected the hint's `if x != null` example, got: $msgs")
  }

  it("fires for a Rust-style `while let Some(x) = ...` pattern binding") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    val opt: String? = "hi"
        |    while let Some(y) = opt {
        |      IO::println(y)
        |    }
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(msgs.contains("var y = opt"), s"expected the hint's `var y = opt` example, got: $msgs")
    assert(msgs.contains("while y != null"), s"expected the hint's `while y != null` example, got: $msgs")
  }
}
