package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A JavaScript/Python-style prefix `yield expr` statement (`ControlFlowSyntaxHints`'s
 * `LeadingYieldStatement` case) must resolve through the bilingual `error.parsing.hint.*`
 * bundle in both locales, like every other hint in that family -- see
 * AwaitStatementHintI18nSpec for the same pattern. Onion has no generator/coroutine
 * `yield`, so the previous generic "missing call parens" fallback (suggesting the
 * nonsensical `yield(...)`) was actively misleading.
 */
class YieldStatementHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.yield_not_supported"
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

  // Both bundles' yield hint spells `do[M]`; neither reserved-word hint does.
  private def assertYieldHint(msgs: String): Unit = {
    assert(msgs.contains("do[M]"), s"expected the do-block hint, got: $msgs")
    assert(!msgs.contains("backticks") && !msgs.contains("バッククォート"),
      s"`yield expr` must not be told to escape `yield` with backticks, got: $msgs")
  }

  it("fires for a JavaScript/Python-style prefix `yield expr` statement") {
    val src =
      """
        |class Main {
        |public:
        |  static def main(args: String[]): void {
        |    yield 5
        |  }
        |}
        |""".stripMargin
    val msgs = compileErrors(src)
    // The hint text is identical in both bundles' relevant markers, so this
    // assertion holds regardless of the JVM's default locale.
    assert(msgs.contains("List"), s"expected the hint's `List` suggestion, got: $msgs")
    assert(!msgs.contains("yield(...)"), s"the misleading missing-call-parens fallback should not fire, got: $msgs")
  }

  // #2300: the hint used to need `yield` to start its source line.
  it("fires for `yield expr` in a one-line block body") {
    assertYieldHint(compileErrors("def f(): Int { yield 1 }\n"))
  }

  it("fires for `yield expr` in a trailing-lambda body") {
    assertYieldHint(compileErrors("val xs = [1, 2].map { x -> yield x * 2 }\n"))
  }

  it("keeps the backtick hint for `yield` used as a name") {
    val msgs = compileErrors("val yield = 1\n")
    assert(msgs.contains("backticks") || msgs.contains("バッククォート"), s"expected the reserved-word hint, got: $msgs")
    assert(!msgs.contains("do[M]"), s"a name is not a `yield expr` statement, got: $msgs")
  }

  private def assertBacktickHint(msgs: String): Unit = {
    assert(msgs.contains("backticks") || msgs.contains("バッククォート"), s"expected the reserved-word hint, got: $msgs")
    assert(!msgs.contains("do[M]"), s"`yield` used as a name must not get the do-block hint, got: $msgs")
  }

  // #2315 panel: a name followed by `->`, a closing token or an infix operator.
  it("keeps the backtick hint for `yield` as a lambda parameter") {
    assertBacktickHint(compileErrors("val xs = [1, 2].map { yield -> yield * 2 }\n"))
  }

  it("keeps the backtick hint for `yield` as an operand") {
    assertBacktickHint(compileErrors("val r = 1 + yield + 2\n"))
  }

  it("keeps the backtick hint for `yield` before a closing token") {
    assertBacktickHint(compileErrors("def f(): Int { return yield }\n"))
  }

  // #2315 panel, second round: a line-final name, a word operator, and a
  // token that cannot start an expression after `yield`.
  it("keeps the backtick hint for `yield` at the end of a line") {
    assertBacktickHint(compileErrors("val r = yield\nprintln(r)\n"))
  }

  it("keeps the backtick hint for `yield` before `as`, `is` or `in`") {
    assertBacktickHint(compileErrors("val c = yield as String\n"))
    assertBacktickHint(compileErrors("val c = yield is String\n"))
    assertBacktickHint(compileErrors("foreach yield in [1, 2] { println(yield) }\n"))
  }

  it("keeps the backtick hint for `yield` before `{`, `..` or `^`") {
    assertBacktickHint(compileErrors("if yield { println(1) }\n"))
    assertBacktickHint(compileErrors("select yield { else: println(1) }\n"))
    assertBacktickHint(compileErrors("val r = yield .. 10\n"))
    assertBacktickHint(compileErrors("val r = yield ^ 1\n"))
  }

  // #2315 panel, third round: any keyword that cannot start an expression
  // after `yield`, and `yield` named by a declaration.
  it("keeps the backtick hint for `yield` before `extends`, `conforms` or `when`") {
    assertBacktickHint(compileErrors("class yield extends Object {}\n"))
    assertBacktickHint(compileErrors("class yield conforms Runnable { public: def run(): void {} }\n"))
    assertBacktickHint(compileErrors("class B[yield extends Object] {}\n"))
    assertBacktickHint(compileErrors("select 1 { case yield when true: println(1) }\n"))
  }

  it("keeps the backtick hint for `yield` right after a declaration keyword") {
    assertBacktickHint(compileErrors("class yield Foo {}\n"))
    assertBacktickHint(compileErrors("val yield x = 1\n"))
  }

  // #2315 panel, fourth round: where only a name can follow (after `.`, `::` or
  // `new`), `yield` is a name even when a space and `(` follow it.
  it("keeps the backtick hint for `yield` after `.`, `::` or `new`") {
    assertBacktickHint(compileErrors(
      "class K { public: def this {}  def `yield`(): String = \"y\" }\nval k = new K()\nprintln(k.yield ())\n"))
    assertBacktickHint(compileErrors(
      "class K { public: static def `yield`(n: Int): Int = n + 1 }\nprintln(K::yield (1))\n"))
    assertBacktickHint(compileErrors(
      "class `yield` { public: def this {} }\nval y = new yield ()\n"))
  }

  it("still gives the do-block hint for `yield` before an expression keyword") {
    assertYieldHint(compileErrors("def f(): Object { yield new Object() }\n"))
    assertYieldHint(compileErrors("def f(): Boolean { yield true }\n"))
  }

  it("still gives the do-block hint for `yield -1` and `yield (1)`") {
    assertYieldHint(compileErrors("def f(): Int { yield -1 }\n"))
    assertYieldHint(compileErrors("def f(): Int { yield (1) }\n"))
  }
}
