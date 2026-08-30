package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * The Java-style generic-constructor-call hints (`SyntaxHintClassifier`'s
 * `JavaStyleGenericConstructorCall` case, covering both the empty diamond `<>`
 * and an explicit `<Type>` argument list) must resolve through the bilingual
 * `error.parsing.hint.*` bundle in both locales, like every other hint in that
 * match -- see ObjectDeclarationHintI18nSpec for the same pattern. Unlike the
 * existing `java_style_generics` hint (a type-annotation position, e.g.
 * `var xs: List<String>`), these fire for `new Foo<...>(...)`, where `<` parses
 * as a valid operator and the real syntax error only surfaces later in the line.
 */
class JavaStyleGenericConstructorHintI18nSpec extends AnyFunSpec with Diagrams {
  private val diamondKey = "error.parsing.hint.java_style_diamond_operator"
  private val genericCallKey = "error.parsing.hint.java_style_generic_constructor_call"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  it("resolves the diamond-operator hint in the English bundle") {
    assert(en.getString(diamondKey).contains("Hint:"))
  }

  it("resolves the diamond-operator hint in the Japanese bundle with actual Japanese text") {
    val text = ja.getString(diamondKey)
    assert(text.contains("ヒント"), s"expected a Japanese hint, got: $text")
    assert(!text.contains("Hint:"), s"hint leaked untranslated English, got: $text")
    assert(text != en.getString(diamondKey))
  }

  it("resolves the generic-constructor-call hint in the English bundle") {
    assert(en.getString(genericCallKey).contains("Hint:"))
  }

  it("resolves the generic-constructor-call hint in the Japanese bundle with actual Japanese text") {
    val text = ja.getString(genericCallKey)
    assert(text.contains("ヒント"), s"expected a Japanese hint, got: $text")
    assert(!text.contains("Hint:"), s"hint leaked untranslated English, got: $text")
    assert(text != en.getString(genericCallKey))
  }

  it("fires for a Java-style empty-diamond constructor call") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |import { java.util.* }
        |class Main {
        |  static def main(args: String[]): void {
        |    val list = new ArrayList<>()
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The substituted class name is literal source text, identical in both bundles,
    // so this assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("new ArrayList(...)"), s"expected the hint's `new ArrayList(...)` example, got: $msgs")
  }

  it("fires for a Java-style generic constructor call with an explicit type argument") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |import { java.util.* }
        |class Main {
        |  static def main(args: String[]): void {
        |    val list = new ArrayList<String>()
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(msgs.contains("ArrayList[String]"), s"expected the hint's `ArrayList[String]` example, got: $msgs")
  }
}
