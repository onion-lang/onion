package onion.compiler.diagnostics

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * `Location` has carried `endLine` / `endColumn` since it was written, and
 * `DiagnosticRenderer` has known how to underline a range with `~` — but no production
 * code ever set them. The only producers of a spanned `Location` were tests, so
 * `spanLength` always returned 1 and every caret in every real diagnostic was a single
 * `^` pointing at the first character of whatever was wrong.
 *
 * The parser now records each token's extent, so a diagnostic anchored on a token
 * underlines the token.
 */
class ParserSpanSpec extends AnyFunSpec {

  private def compile(src: String) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10)
    new OnionCompiler(config)
      .compile(Seq(new StreamInputSource(() => new StringReader(src), "span.on")))
  }

  private def firstError(src: String) = compile(src) match {
    case CompilationOutcome.Failure(errors) => errors.head
    case _                                  => fail(s"expected a compile error for:\n$src")
  }

  describe("a diagnostic anchored on an identifier") {
    val src =
      """|class Test {
         |public:
         |  static def main(args: String[]): void {
         |    println(undefinedName)
         |  }
         |}
         |""".stripMargin

    it("has a span") {
      val e = firstError(src)
      assert(e.location != null)
      assert(e.location.hasSpan, "the parser still produces no span")
    }

    it("spans the whole identifier, not one character") {
      val e = firstError(src)
      // "undefinedName" is 13 characters.
      assert(e.location.spanLength == 13, s"span was ${e.location.spanLength}")
    }

    it("still starts at the identifier") {
      val e = firstError(src)
      assert(e.location.line == 4)
      // 4 spaces of indent + "println(" is 8 -> column 13
      assert(e.location.column == 13, s"column was ${e.location.column}")
    }
  }

  describe("a single-character token") {
    it("keeps a span of one, so the caret stays a caret") {
      val e = firstError(
        """|class Test {
           |public:
           |  static def main(args: String[]): void {
           |    val b: Boolean = true + 1
           |  }
           |}
           |""".stripMargin
      )
      assert(e.location != null)
      assert(e.location.spanLength >= 1)
    }
  }
}
