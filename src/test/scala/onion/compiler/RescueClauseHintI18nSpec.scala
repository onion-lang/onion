package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A Ruby-style `rescue` clause on a `try` block (`ControlFlowSyntaxHints`'s
 * `RescueClause` case) must resolve through the bilingual `error.parsing.hint.*`
 * bundle in both locales, like every other hint in that family -- see
 * ExceptClauseHintI18nSpec for the same pattern. Onion has no `rescue`
 * clause; exceptions are caught with `catch`, so a bare
 * `try { ... } rescue e: Exception { ... }` previously had no dedicated
 * diagnostic pointing at it.
 */
class RescueClauseHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.rescue_not_supported"
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

  it("fires for a Ruby-style `rescue` clause on a `try` block") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    try {
        |      IO::println("risky")
        |    } rescue e: Exception {
        |      IO::println("caught")
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
    assert(msgs.contains("catch"), s"expected the hint's `catch` suggestion, got: $msgs")
  }
}
