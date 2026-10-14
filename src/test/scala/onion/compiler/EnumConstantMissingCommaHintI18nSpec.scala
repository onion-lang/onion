package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A homogeneous (data-carrying) enum's constants need a literal `,` between them;
 * writing them one per line, as every other brace-delimited list in Onion allows,
 * is a plain syntax error with no hint that a comma is the fix (issue #2096). The
 * hint must resolve through the bilingual `error.parsing.hint.*` bundle in both
 * locales, like every other hint in `SyntaxHintClassifier`'s match -- see
 * CompanionObjectDeclarationHintI18nSpec for the same pattern.
 */
class EnumConstantMissingCommaHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.enum_constant_missing_comma"
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

  it("fires for a data-carrying enum's constants written one per line") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |enum Planet(mass: Double) {
        |  MERCURY(3.3)
        |  VENUS(4.87)
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The hint names the offending constant inside a literal `, VENUS(` snippet,
    // identical in both bundles, so this assertion holds regardless of locale.
    assert(msgs.contains(", VENUS("), s"expected the enum-constant-missing-comma hint, got: $msgs")
  }
}
