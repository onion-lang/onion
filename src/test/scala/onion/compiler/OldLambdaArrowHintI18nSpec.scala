package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A standalone (non-trailing) lambda still using the old `=>` arrow --
 * `val f = (x) => x * 2` -- is the same mistake as `OldTrailingArrowHintI18nSpec`
 * covers for trailing lambdas, but it trips the parser on a different token
 * (`=`, not `{`), so `SyntaxHintClassifier` needs its own case
 * (`old_lambda_arrow`). The message must also resolve through the bilingual
 * `error.parsing.hint.*` bundle in both locales; see OldForInHintI18nSpec for
 * the motivating regression.
 */
class OldLambdaArrowHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.old_lambda_arrow"
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

  private def compile(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  it("fires for a standalone lambda still using the old `=>`") {
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): Int {
        |    val f = (x) => x * 2
        |    return f(1)
        |  }
        |}
        |""".stripMargin
    val msgs = compile(src)
    // The example code shown in the hint is literal, identical in both bundles,
    // so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("(x) -> ..."), s"expected the hint's `(x) -> ...` example, got: $msgs")
  }

  it("fires for a bare-parameter lambda still using the old `=>`") {
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): Int {
        |    val f: Int -> Int = x => x * 2
        |    return f(1)
        |  }
        |}
        |""".stripMargin
    val msgs = compile(src)
    assert(msgs.contains("(x) -> ..."), s"expected the hint's `(x) -> ...` example, got: $msgs")
  }

  it("does not fire for a Ruby-style `key => value` map-literal mistake") {
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): Int {
        |    val m = ["a" => 1]
        |    return 0
        |  }
        |}
        |""".stripMargin
    val msgs = compile(src)
    assert(!msgs.contains("(x) -> ..."), s"lambda-arrow hint should not fire on a map literal, got: $msgs")
  }
}
