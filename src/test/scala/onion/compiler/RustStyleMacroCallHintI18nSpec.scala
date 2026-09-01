package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The Rust-style `name!(...)` macro-call hint (`SyntaxHintClassifier`'s `"!"`
 * case) must resolve through the bilingual `error.parsing.hint.*` bundle in
 * both locales, like every other hint in that match -- see
 * DollarSigilHintI18nSpec for the same pattern.
 */
class RustStyleMacroCallHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.rust_style_macro_call"
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

  it("fires for a Rust-style `println!(...)` macro call") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    println!("hi")
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The substituted macro name is a literal argument, identical in both
    // bundles, so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("println(...)"), s"expected the hint's `println(...)` rewrite, got: $msgs")
  }

  it("fires for a Rust-style macro call with an underscore in the name, e.g. `assert_eq!`") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    val x = 1
        |    val y = 1
        |    assert_eq!(x, y)
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(msgs.contains("assert_eq(...)"), s"expected the hint's `assert_eq(...)` rewrite, got: $msgs")
  }
}
