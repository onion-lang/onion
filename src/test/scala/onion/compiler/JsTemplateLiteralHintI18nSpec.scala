package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A JS/TS-style backtick template literal (`` `Hello ${name}` ``) is not a string
 * in Onion -- backticks only escape a reserved word into an identifier (see
 * `` `class` `` in CLAUDE.md), so the whole ``${name}`` text becomes the literal
 * name of a local variable reference. That variable is never declared, so the
 * mistake surfaces as a VARIABLE_NOT_FOUND (E0002) whose message repeats the
 * entire template text as if it were an identifier, with nothing connecting it
 * back to the actual mistake. `SemanticErrorReporter.reportVariableNotFound`
 * recognizes the `${...}` shape in the unresolved name and points at Onion's
 * actual string interpolation syntax, `"text #{expr}"`, instead.
 */
class JsTemplateLiteralHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "suggestion.jsTemplateLiteral"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  it("resolves in the English bundle") {
    assert(en.getString(key).contains("#{"))
  }

  it("resolves in the Japanese bundle with actual Japanese text") {
    val text = ja.getString(key)
    assert(text.contains("#{"), s"expected the interpolation example, got: $text")
    assert(text != en.getString(key))
  }

  it("fires for a JS/TS-style backtick template literal used as a string") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    val name = "world"
        |    IO::println(`Hello ${name}`)
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The hint's interpolation example is identical in both bundles, so this
    // assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("#{"), s"expected the JS-template-literal hint, got: $msgs")
  }

  it("does not fire for an ordinary unresolved variable name") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    IO::println(doesNotExist)
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(!msgs.contains("#{"), s"did not expect the JS-template-literal hint, got: $msgs")
  }
}
