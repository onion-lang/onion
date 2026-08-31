package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The destructuring `for (a, b) in coll` mistake hint (`SyntaxHintClassifier`'s
 * `ForEachDestructureMistake`/`ForEachDestructureWrappedMistake` cases -- `for`
 * doesn't destructure or iterate, that's `foreach`'s job) must resolve through
 * the bilingual `error.parsing.hint.*` bundle in both locales, like every
 * other hint in that match -- see GuardElseHintI18nSpec for the same pattern.
 */
class ForEachDestructureHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.for_each_destructure"
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

  it("fires for a `for (key, value) in entries { ... }` destructuring mistake") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    val entries: Map = ["a": 1, "b": 2]
        |    for (key, value) in entries {
        |      IO::println(key)
        |    }
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The substituted variable/collection text is literal source text,
    // identical in both bundles, so this assertion holds regardless of the
    // JVM's default locale.
    assert(msgs.contains("foreach (key, value) in entries"), s"expected the hint's `foreach` example, got: $msgs")
  }
}
