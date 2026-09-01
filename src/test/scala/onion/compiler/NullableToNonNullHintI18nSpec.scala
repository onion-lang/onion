package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * `SemanticErrorReporter.reportIncompatibleType` appends a bilingual
 * `suggestion.nullableToNonNull` hint when a nullable value (`T?`) flows into a
 * position that expects a non-null `T`, pointing at `!!`/`?:`/a null check. The hint
 * carries a bilingual `errorMessage*.properties` entry but had no dedicated
 * i18n-robustness test, unlike the parser-classifier hints covered by the other
 * `*HintI18nSpec` files.
 */
class NullableToNonNullHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "suggestion.nullableToNonNull"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  private val src =
    """
      |class Main {
      |public:
      |  static def take(s: String?): void {
      |    val n: String = s
      |  }
      |  static def main(args: String[]): Int {
      |    take("x")
      |    return 0
      |  }
      |}
      |""".stripMargin

  private def compileErrors(): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  it("resolves in the English bundle") {
    assert(en.getString(key).nonEmpty)
  }

  it("resolves in the Japanese bundle with actual Japanese text, distinct from English") {
    val text = ja.getString(key)
    assert(!text.toLowerCase(java.util.Locale.ROOT).contains("nullable"), s"leaked untranslated English, got: $text")
    assert(text != en.getString(key))
  }

  it("fires the hint when a nullable value is assigned to a non-null local") {
    val msgs = compileErrors()
    // `!!` and `?:` are literal operator tokens, identical in both bundles, so this
    // assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("!!") && msgs.contains("?:"), s"expected the nullable-to-non-null hint, got: $msgs")
  }
}
