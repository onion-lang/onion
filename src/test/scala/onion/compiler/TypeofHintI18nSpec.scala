package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A JavaScript/Python-style `typeof expr` runtime type check
 * (`SyntaxHintClassifier`'s `TypeofCondition` case) must resolve through the
 * bilingual `error.parsing.hint.*` bundle in both locales, like every other
 * hint in that family -- see InstanceofHintI18nSpec for the same pattern.
 * `typeof` isn't a keyword in Onion, so it parses as a bare identifier and
 * the parser trips on whatever follows it, never on `typeof` itself; the
 * generic "a block is expected here" fallback pointed nowhere near the
 * actual mistake. Onion's type check is the `is` operator (`expr is Type`).
 */
class TypeofHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.typeof_not_supported"
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

  it("fires for a JS/Python-style `typeof expr` condition") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    val x: Object = "hi"
        |    if typeof x == "string" {
        |      IO::println("yes")
        |    }
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The substituted expression is literal source text, identical in both
    // bundles, so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("x is"), s"expected the hint's `is` rewrite, got: $msgs")
    assert(msgs.contains("typeof x"), s"expected the hint to echo the mistake, got: $msgs")
  }
}
