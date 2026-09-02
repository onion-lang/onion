package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A Ruby-style bare `puts(...)` call has no equivalent in Onion -- there is no
 * default-imported `puts` function -- so it surfaces as an ordinary METHOD_NOT_FOUND
 * (E0005) on the enclosing script's synthesized main class, with nothing connecting it
 * back to Onion's actual output API. `SemanticErrorReporter.reportMethodNotFound`
 * recognizes an unqualified call to the exact name `puts` and points at `IO::println`
 * instead of a name-similarity guess.
 */
class RubyStylePutsHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "suggestion.rubyPuts"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  it("resolves in the English bundle") {
    assert(en.getString(key).contains("IO::println"))
  }

  it("resolves in the Japanese bundle with actual Japanese text") {
    val text = ja.getString(key)
    assert(text.contains("IO::println"), s"expected the IO::println hint, got: $text")
    assert(text != en.getString(key))
  }

  it("fires for a Ruby-style `puts(...)` call with an argument") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    puts("hi")
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(msgs.contains("IO::println"), s"expected the puts hint, got: $msgs")
  }

  it("fires for a Ruby-style bare `puts()` call") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    puts()
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(msgs.contains("IO::println"), s"expected the puts hint, got: $msgs")
  }

  it("does not fire for an ordinary unresolved method call") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    doesNotExist("hi")
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(!msgs.contains("IO::println"), s"did not expect the puts hint, got: $msgs")
  }

  it("does not fire when the class defines its own `puts` method with a mismatched call") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    puts(1, 2, 3)
        |  }
        |  static def puts(x: Int): void { }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(!msgs.contains("IO::println"), s"did not expect the puts hint, got: $msgs")
  }
}
