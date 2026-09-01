package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * `SemanticErrorReporter.reportMethodNotFound` appends a bilingual
 * `suggestion.fieldNotMethod` hint when a call like `arr.length()` resolves to a
 * same-named field/property instead of a method, pointing at the paren-less form.
 * The hint carries a bilingual `errorMessage*.properties` entry but had no dedicated
 * i18n-robustness test, unlike the parser-classifier hints covered by the other
 * `*HintI18nSpec` files.
 */
class FieldNotMethodHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "suggestion.fieldNotMethod"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  private val src =
    """
      |class Main {
      |public:
      |  static def main(args: String[]): Int {
      |    val arr: Int[] = new Int[3]
      |    val n = arr.length()
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
    assert(!text.toLowerCase(java.util.Locale.ROOT).contains("field"), s"leaked untranslated English, got: $text")
    assert(text != en.getString(key))
  }

  it("fires the hint when a paren-less field/property is called like a method") {
    val msgs = compileErrors()
    // "length" is the property name, identical literal text in both bundles, so this
    // assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("length"), s"expected the field-not-method hint naming 'length', got: $msgs")
  }
}
