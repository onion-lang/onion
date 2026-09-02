package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A JavaScript/Python/Rust-style prefix `async def`/`async function`/`async fn`
 * declaration (`ControlFlowSyntaxHints`'s `LeadingAsyncFunctionDeclaration` case)
 * must resolve through the bilingual `error.parsing.hint.*` bundle in both locales,
 * like every other hint in that family -- see AwaitStatementHintI18nSpec and
 * DeferStatementHintI18nSpec for the same pattern. Onion has no `async` keyword, so
 * `async` parsed as a bare identifier statement and the following declaration
 * keyword fell through to the generic "missing call parens" fallback, suggesting
 * the nonsensical `async(...)`.
 */
class AsyncFunctionDeclarationHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.async_function_not_supported"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  it("resolves the key in the English bundle") {
    assert(en.getString(key).contains("Hint:"))
  }

  it("resolves the key in the Japanese bundle with actual Japanese text") {
    val text = ja.getString(key)
    assert(text.contains("ヒント"), s"expected a Japanese hint, got: $text")
    assert(!text.contains("Hint:"), s"hint leaked untranslated English, got: $text")
    assert(text != en.getString(key))
  }

  private def compileErrors(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  it("fires for a Python-style prefix `async def name(...):` declaration") {
    val src =
      """
        |async def fetchData(url: String):
        |  return url
        |""".stripMargin
    val msgs = compileErrors(src)
    assert(msgs.contains("Future::async"), s"expected the hint's `Future::async` example, got: $msgs")
    assert(!msgs.contains("async(fetchData)"), s"the misleading missing-call-parens fallback should not fire, got: $msgs")
  }

  it("fires for a JavaScript-style prefix `async function name(...)` declaration") {
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    async function fetchData(url) {
        |      return url
        |    }
        |  }
        |}
        |""".stripMargin
    val msgs = compileErrors(src)
    assert(msgs.contains("Future::async"), s"expected the hint's `Future::async` example, got: $msgs")
  }
}
