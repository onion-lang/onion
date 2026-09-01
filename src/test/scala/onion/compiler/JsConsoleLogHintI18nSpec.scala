package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A JS-style `console.log(...)`/`console.error(...)`/`console.warn(...)` call has no
 * `console` object in Onion, so `console` parses as a bare identifier reference and the
 * mistake surfaces as a VARIABLE_NOT_FOUND (E0002) on `console` with nothing connecting
 * it back to Onion's actual output API. `SemanticErrorReporter.reportVariableNotFound`
 * recognizes the exact unresolved name `console` and points at `IO::println` instead.
 */
class JsConsoleLogHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "suggestion.jsConsoleLog"
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

  it("fires for a JS-style `console.log(...)` call") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    console.log("hi")
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(msgs.contains("IO::println"), s"expected the console.log hint, got: $msgs")
  }

  it("fires for a JS-style `console.error(...)` call") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    console.error("boom")
        |  }
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    assert(msgs.contains("IO::println"), s"expected the console.log hint, got: $msgs")
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
    assert(!msgs.contains("IO::println"), s"did not expect the console.log hint, got: $msgs")
  }
}
