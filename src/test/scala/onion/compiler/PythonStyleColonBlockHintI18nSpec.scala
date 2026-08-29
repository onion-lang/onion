package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The Python-style-colon-block hint (`SyntaxHintClassifier`'s `PythonStyleColonBlockHeader`
 * case) must resolve through the bilingual `error.parsing.hint.*` bundle in both locales,
 * like every other hint in that match -- see OldForInHintI18nSpec for the motivating
 * regression.
 */
class PythonStyleColonBlockHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.python_style_colon_block"
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

  it("fires for a Python-style `if cond:` header") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): Int {
        |    if args.length > 0:
        |      IO::println("hi")
        |    return 0
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The rewritten header shown in the hint is literal code, identical in both bundles,
    // so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("if args.length > 0"), s"expected the hint's rewritten header, got: $msgs")
  }

  it("fires for a Python-style `while cond:` header") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): Int {
        |    while args.length > 0:
        |      IO::println("hi")
        |    return 0
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(msgs.contains("while args.length > 0"), s"expected the hint's rewritten header, got: $msgs")
  }
}
