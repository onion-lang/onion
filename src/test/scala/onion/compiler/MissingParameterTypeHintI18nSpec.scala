package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A parameter without an explicit type (Python/JS/TS style, where types are
 * optional or inferred) used to be misclassified by `SyntaxHintClassifier` as
 * a Java-style method declaration (`JavaStyleMethodDeclaration` also matches
 * `def name(` since the pattern only excludes a leading `public`/`private`/
 * `protected`, not `def`), producing a nonsensical hint that told the user to
 * write `def`, which the line already did. This hint -- and the fix that
 * excludes `def` from that other pattern -- must resolve through the
 * bilingual `error.parsing.hint.*` bundle in both locales, like every other
 * hint in that match -- see OldForInHintI18nSpec for the motivating
 * regression.
 */
class MissingParameterTypeHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.missing_parameter_type"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  private def messages(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  it("resolves in the English bundle") {
    assert(en.getString(key).contains("Hint:"))
  }

  it("resolves in the Japanese bundle with actual Japanese text") {
    val text = ja.getString(key)
    assert(text.contains("ヒント"), s"expected a Japanese hint, got: $text")
    assert(!text.contains("Hint:"), s"hint leaked untranslated English, got: $text")
    assert(text != en.getString(key))
  }

  it("hints at a missing parameter type, not a Java-style method, for `def foo(x): Int`") {
    val msgs = messages("def foo(x): Int { ret x }\n")
    assert(msgs.contains("x"), s"expected the hint to name the untyped parameter, got: $msgs")
    assert(!msgs.contains("public void method"), s"should not fire the Java-style-method hint, got: $msgs")
  }

  it("does not fire when every parameter already has a type") {
    val msgs = messages("def foo(x: Int): Int { ret x }\n")
    assert(!msgs.contains("needs an explicit type"), s"hint should not fire for a fully-typed parameter list, got: $msgs")
  }
}
