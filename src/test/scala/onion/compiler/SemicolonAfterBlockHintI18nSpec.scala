package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * A `;` right after a block statement's closing `}` (`while c { ... }; next()`)
 * is a syntax error whose expected-token list names top-level declarations.
 * The hint says the `;` is the problem (#2292).
 */
class SemicolonAfterBlockHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.semicolon_after_block"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  private def compileErrors(src: String): String = {
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

  it("fires for a `;` after a top-level `while` block") {
    val msgs = compileErrors("var i = 0\nwhile i < 2 { i += 1 }; println(i)\n")
    assert(msgs.contains("`;`"), s"expected the hint to mention `;`, got: $msgs")
    assert(msgs.contains("`}`"), s"expected the hint to mention `}`, got: $msgs")
  }

  it("fires for a `;` after an `if` block inside a method body") {
    val msgs = compileErrors("def main(args: String[]): void {\n  if true { println(1) }  ; println(2)\n}\n")
    assert(msgs.contains("`;`"), s"expected the hint to mention `;`, got: $msgs")
    assert(msgs.contains("`}`"), s"expected the hint to mention `}`, got: $msgs")
  }

  it("stays out of a doubled `;` that follows no `}`") {
    val msgs = compileErrors("val x = 1;;\n")
    assert(msgs.nonEmpty)
    assert(!msgs.contains("`}`"), s"did not expect the block hint, got: $msgs")
  }

  it("still accepts a `;` after an expression that ends in `}`") {
    assert(compileErrors("val xs = [1].map { x -> x * 2 }; println(xs)\n").isEmpty)
  }

  it("stays out of a tab-indented line where no `}` precedes the `;` (#2312)") {
    val msgs = compileErrors("def main(args: String[]): void {\n\tval n = (1;  }\n}\n")
    assert(msgs.nonEmpty)
    assert(!msgs.contains("`}`"), s"did not expect the block hint, got: $msgs")
  }

  it("still fires after a `}` on a tab-indented line (#2312)") {
    val msgs = compileErrors("def main(args: String[]): void {\n\tif true { println(1) }; println(2)\n}\n")
    assert(msgs.contains("`}`"), s"expected the block hint, got: $msgs")
  }
}
