package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A Python-style `with expr as name { ... }` resource-management block
 * (`ControlFlowSyntaxHints`'s `LeadingWithAsStatement` case) must resolve
 * through the bilingual `error.parsing.hint.*` bundle in both locales, like
 * every other hint in that family -- see AwaitStatementHintI18nSpec for the
 * same pattern. `with` is not a keyword in Onion, so this previously read as
 * a bare `with` identifier followed by another expression with nothing in
 * between, hitting the generic "missing call parens" fallback (suggesting
 * the nonsensical `with(...)`), which pointed nowhere near Onion's actual
 * equivalent, `try (val name = expr) { ... }`.
 */
class WithAsStatementHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.with_as_statement"
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

  it("fires for a Python-style `with expr as name { ... }` block") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    with openResource() as f {
        |      IO::println(f)
        |    }
        |  }
        |  static def openResource(): Int = 1
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The substituted name/expression are literal source text, identical in
    // both bundles, so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("try (val f = openResource())"), s"expected the hint's try-with-resources example, got: $msgs")
  }
}
