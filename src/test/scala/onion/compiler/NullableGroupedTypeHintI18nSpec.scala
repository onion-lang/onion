package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The nullable-grouped-type hint (`SyntaxHintClassifier`'s case for a `?`
 * where the grammar's grouped/function-type production wants `->` next)
 * must resolve through the bilingual `error.parsing.hint.*` bundle in both
 * locales, like every other hint in that match -- see
 * NullableUnionTypeHintI18nSpec for the same pattern.
 *
 * Onion has no grouped type `(T)`: the parser's `(` branch inside a type
 * always reads a function-type parameter list and then requires `->`, so
 * `(String)?` or `((Int) -> Int)?` both fail right at the `?` with "expecting
 * \"->\"" -- a plain `?` there, with nothing else in play, would otherwise
 * fall through to the unrelated ternary-operator hint.
 */
class NullableGroupedTypeHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.nullable_grouped_type"
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

  it("fires for a nullable arrow-syntax function type wrapped in parens") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val fn: ((Int) -> Int)? = null
        |    IO::println(fn)
        |    return 0
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The example text is literal, identical in both bundles, so this
    // assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("Function1"), s"expected the hint's `Function1[...]?` example, got: $msgs")
  }

  it("also fires for a plain (non-function) parenthesized type") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Test {
        |public:
        |  static def main(args: String[]): Int {
        |    val x: (String)? = null
        |    IO::println(x)
        |    return 0
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(msgs.contains("Function1"), s"expected the hint's `Function1[...]?` example, got: $msgs")
  }
}
