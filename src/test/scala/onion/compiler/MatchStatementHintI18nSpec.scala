package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A Rust/Scala/OCaml-style `match` expression (`ControlFlowSyntaxHints`'s
 * `LeadingMatchStatement` case) must resolve through the bilingual
 * `error.parsing.hint.*` bundle in both locales, like every other hint in
 * that family -- see SwitchStatementHintI18nSpec for the same pattern. Onion
 * has no `match` expression; `select` covers the same ground, so a bare
 * `match value { ... }` previously had no dedicated diagnostic pointing at it.
 */
class MatchStatementHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.match_not_supported"
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

  it("fires for a Rust/Scala/OCaml-style `match` expression") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    val x: Int = 1
        |    match x {
        |      case 1:
        |        IO::println("one")
        |    }
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The hint text is identical in both bundles' relevant markers, so this
    // assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("select"), s"expected the hint's `select` suggestion, got: $msgs")
  }
}
