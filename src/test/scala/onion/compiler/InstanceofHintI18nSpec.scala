package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A Java/JavaScript/PHP-style `expr instanceof Type` runtime type check
 * (`SyntaxHintClassifier`'s `InstanceofExpression` case) must resolve through
 * the bilingual `error.parsing.hint.*` bundle in both locales, like every
 * other hint in that family -- see PythonStyleRaiseHintI18nSpec for the same
 * pattern. Onion's type check is the `is` operator (`expr is Type`, also
 * usable as `case x is Type:` in `select`), so `instanceof` previously fell
 * through to the generic "a block is expected here" fallback, which pointed
 * nowhere near the actual mistake.
 */
class InstanceofHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.instanceof_not_supported"
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

  it("fires for a Java-style `expr instanceof Type` condition") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    val x: Object = "hi"
        |    if x instanceof String {
        |      IO::println("yes")
        |    }
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The substituted expression/type are literal source text, identical in
    // both bundles, so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("x is String"), s"expected the hint's `is` rewrite, got: $msgs")
    assert(msgs.contains("x instanceof String"), s"expected the hint to echo the mistake, got: $msgs")
  }
}
