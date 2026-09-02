package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A Python-style f-string prefix (`` f"Hello {name}" ``) is not a string literal in
 * Onion -- any bare identifier directly before a string literal desugars to a call
 * to a function named after that identifier (the scheme-literal sugar documented in
 * CLAUDE.md, e.g. `re"..."`/`file"..."`), so `f"Hello {name}"` parses fine as
 * `f("Hello {name}")` and fails only because no function named `f` exists. The
 * resulting METHOD_NOT_FOUND (E0005) message names `f` and the raw `{name}` text with
 * nothing connecting it back to the actual mistake. `SemanticErrorReporter.
 * reportMethodNotFound` recognizes this shape (an unresolved single-String-argument
 * call named `f`) and points at Onion's actual string interpolation syntax,
 * `"text #{expr}"`, instead.
 */
class PythonStyleFStringHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "suggestion.pythonFString"
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

  it("fires for a Python-style f-string prefix used as a string") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    val name = "world"
        |    IO::println(f"Hello {name}")
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    // The hint's interpolation example is identical in both bundles, so this
    // assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("#{"), s"expected the Python-f-string hint, got: $msgs")
  }

  it("does not fire for an ordinary unresolved function call") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    IO::println(doesNotExist("hello"))
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(!msgs.contains("#{"), s"did not expect the Python-f-string hint, got: $msgs")
  }

  it("does not fire for a call to f with a non-String argument") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    IO::println(f(42))
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(!msgs.contains("#{"), s"did not expect the Python-f-string hint, got: $msgs")
  }
}
