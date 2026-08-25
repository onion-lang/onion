package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The symbolic `:` spelling of superclass inheritance, replaced by `extends`.
 * `SyntaxHintClassifier`'s `case ":" if expected.contains("extends")` fires when the
 * parser trips on `:` right where a superclass name would follow `extends` --
 * like every other hint in that match, the message must resolve through the
 * bilingual `error.parsing.hint.*` bundle in both locales; see
 * OldForInHintI18nSpec for the motivating regression.
 */
class OldExtendsHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.old_extends"
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

  it("fires for the old `class A : B { }` superclass syntax") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class B { }
        |class A : B {
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The example code shown in the hint is literal, identical in both bundles,
    // so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("class A extends B"), s"expected the hint's `class A extends B` example, got: $msgs")
  }
}
