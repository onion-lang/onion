package onion.compiler.tools

import java.io.StringReader

import onion.compiler.{CompileError, CompilerConfig, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/**
 * A diagnostic should underline the construct it is about.
 *
 * Every leaf already spanned its own token, but a compound expression was anchored at its
 * operator and a call at its name — so `3 * "x"` got a single `^` under the `*`, and
 * `someUndefinedThing(1, 2)` underlined only the eighteen characters of the name and left
 * the arguments out of the error entirely.
 *
 * These assertions are on the span length rather than on the rendered text, because the
 * message itself is bilingual and the point is the extent, not the wording.
 */
class DiagnosticSpanSpec extends AnyFunSpec {

  private def errors(source: String): Seq[CompileError] =
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, checkLaws = false)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "Span.on")))
      .allErrors.toSeq

  private def wrap(body: String): String =
    s"""class Span {
       |public:
       |  static def main(args: String[]): Int {
       |$body
       |    return 0
       |  }
       |}
       |""".stripMargin

  /** The span of the first error, as (column, length). */
  private def firstSpan(body: String): (Int, Int) =
    val all = errors(wrap(body))
    assert(all.nonEmpty, "expected a compile error")
    val location = all.head.location
    assert(location != null, "the error carries no location")
    (location.column, location.spanLength)

  describe("a binary expression") {
    it("underlines both operands and the operator, not just the operator") {
      // `3 * "x"` is seven characters starting at column 18.
      val (column, length) = firstSpan("""    val n: Int = 3 * "x"""")
      assert(length == 7, s"underlined $length characters from column $column")
      assert(column == 18, s"anchored at column $column")
    }

    it("covers a whole chain of operators of the same precedence") {
      // `1 + 2 + "x"` -- the outer node's left operand is itself a binary expression, so
      // this only holds if spans compose.
      val (_, length) = firstSpan("""    val n: Int = 1 + 2 + "x"""")
      assert(length == 11, s"underlined $length characters")
    }
  }

  describe("a method call") {
    it("underlines its arguments as well as its name") {
      // `someUndefinedThing(1, 2)` is 24 characters.
      val (_, length) = firstSpan("""    val n: Int = someUndefinedThing(1, 2)""")
      assert(length == 24, s"underlined $length characters")
    }

    it("underlines an instance call from the name through the closing paren") {
      // `noSuchMethod(1, 2, 3)` is 21 characters: the receiver stays out, since a chain
      // would otherwise make every link underline everything before it.
      val (_, length) = firstSpan("""    val s: String = "a".noSuchMethod(1, 2, 3)""")
      assert(length == 21, s"underlined $length characters")
    }

    it("underlines a static call the same way") {
      // `noSuchStatic("a", "b")` is 22 characters.
      val (_, length) = firstSpan("""    val q: Int = Strings::noSuchStatic("a", "b")""")
      assert(length == 22, s"underlined $length characters")
    }
  }

  describe("a leaf") {
    it("still spans exactly its own token") {
      // The pre-existing behaviour, which the compound spans are built on: if a leaf ever
      // stopped spanning its token, every span above it would silently shrink to one caret.
      val (_, length) = firstSpan("""    val n: Int = undefinedName""")
      assert(length == 13, s"underlined $length characters")
    }
  }
}
