package onion.tools.lsp

import org.eclipse.lsp4j.SymbolKind
import org.scalatest.funspec.AnyFunSpec

import scala.jdk.CollectionConverters.*

/**
 * Semantic tokens.
 *
 * The protocol delta-encodes tokens as five integers each, relative to the token before, so
 * a single misplaced entry shifts the colouring of everything after it. These tests decode
 * the stream back into absolute positions and assert on those — asserting on raw deltas
 * would pass for an encoder that is internally consistent and wrong.
 */
class OnionSemanticTokensSpec extends AnyFunSpec {

  private final case class Decoded(line: Int, start: Int, length: Int, tokenType: String, declaration: Boolean)

  private def decode(
    content: String,
    symbols: Map[String, SymbolKind] = Map.empty,
    declarations: Set[(String, Int)] = Set.empty
  ): Seq[Decoded] = {
    val raw = OnionSemanticTokens
      .encode(content, symbols.get, (name, line, _) => declarations.contains((name, line)))
      .asScala.map(_.intValue).toSeq
    assert(raw.size % 5 == 0, "the protocol requires exactly five integers per token")
    var line = 0
    var start = 0
    raw.grouped(5).map { case Seq(deltaLine, deltaStart, length, tokenType, modifiers) =>
      line += deltaLine
      start = if (deltaLine == 0) start + deltaStart else deltaStart
      Decoded(line, start, length, OnionSemanticTokens.TokenTypes.get(tokenType), (modifiers & 1) != 0)
    }.toSeq
  }

  private def at(tokens: Seq[Decoded], line: Int, start: Int): Option[Decoded] =
    tokens.find(t => t.line == line && t.start == start)

  describe("what only a language server can know") {
    it("colours an identifier by what the document declares it to be") {
      // This is the whole point: to a TextMate grammar `Greeter`, `greet` and `count` are
      // the same thing -- a word.
      val content = "class Greeter { def greet(): Int = count }\n"
      val tokens = decode(
        content,
        Map("Greeter" -> SymbolKind.Class, "greet" -> SymbolKind.Method, "count" -> SymbolKind.Field))

      assert(at(tokens, 0, 6).map(_.tokenType).contains("class"))
      assert(at(tokens, 0, 20).map(_.tokenType).contains("method"))
      assert(at(tokens, 0, 35).map(_.tokenType).contains("property"))
    }

    it("emits nothing for an identifier the document does not declare") {
      // Semantic tokens override the grammar, so a guess replaces a right answer with a
      // wrong one. Silence lets the grammar keep its result.
      val tokens = decode("val x = somethingUnknown\n")
      assert(at(tokens, 0, 8).isEmpty)
    }

    it("marks a declaration site with the declaration modifier") {
      val tokens = decode(
        "class Greeter { }\n",
        Map("Greeter" -> SymbolKind.Class),
        Set(("Greeter", 0)))
      assert(at(tokens, 0, 6).exists(_.declaration))
    }

    it("knows the language's own types, which no document declares") {
      val tokens = decode("val n: Int = 1\n")
      assert(at(tokens, 0, 7).map(_.tokenType).contains("type"))
    }
  }

  describe("the lexical layer") {
    it("classifies keywords, strings and numbers") {
      val tokens = decode("val x = \"hi\"\n")
      assert(at(tokens, 0, 0).map(_.tokenType).contains("keyword"))
      assert(at(tokens, 0, 8).map(_.tokenType).contains("string"))
      assert(decode("val n = 42\n").exists(t => t.tokenType == "number" && t.length == 2))
    }

    it("distinguishes a regex literal from any other scheme literal") {
      assert(decode("val p = re\"\\d+\"\n").exists(_.tokenType == "regexp"))
      assert(decode("val f = file\"a.txt\"\n").exists(_.tokenType == "string"))
    }

    it("derives keywords from the parser, so a new one needs no edit here") {
      // The TextMate grammar rotted to 70% coverage precisely because its keyword list was
      // maintained by hand. This one reads the generated parser's own token table.
      Seq("record", "enum", "extends", "sealed", "abstract", "throw").foreach { keyword =>
        val tokens = decode(s"$keyword X\n")
        assert(at(tokens, 0, 0).map(_.tokenType).contains("keyword"), keyword)
      }
    }

    it("marks operators but not brackets or separators") {
      // No theme colours punctuation distinctly, and marking it would override the grammar
      // with something no more informative.
      val tokens = decode("val n = a + b\n")
      assert(at(tokens, 0, 10).map(_.tokenType).contains("operator"))
      assert(decode("f(a, b)\n").forall(_.tokenType != "operator"))
    }
  }

  describe("what it deliberately leaves to the grammar") {
    it("says nothing about a soft keyword, which is an identifier to the lexer") {
      // `conforms` is only a keyword where the parser decides by lookahead that it is one.
      // The lexer hands it over as an identifier, and guessing from that would colour a
      // method named `conforms` as a keyword. The TextMate grammar pins it with its own
      // lookahead, and this stays out of the way.
      assert(at(decode("class A conforms I { }\n"), 0, 8).isEmpty)
    }
  }

  describe("encoding hazards") {
    it("splits a token that spans lines, which the protocol cannot express") {
      val tokens = decode("/* one\n   two */\nval x = 1\n")
      val comments = tokens.filter(_.tokenType == "comment")
      assert(comments.size == 2, comments)
      assert(comments.map(_.line) == Seq(0, 1))
    }

    it("emits tokens in source order, since each is relative to the one before") {
      val tokens = decode("val a = 1\nval b = 2\nval c = 3\n")
      assert(tokens.map(t => (t.line, t.start)) == tokens.map(t => (t.line, t.start)).sorted)
    }

    it("lands on the right column when the line is indented with tabs") {
      // JavaCC advances a column to the next multiple of eight for a tab, so a token on a
      // tab-indented line reports a column that is not a character index. Every token on
      // such a line would otherwise be placed too far right.
      val tokens = decode("class C {\n\tval x = 1\n}\n")
      assert(at(tokens, 1, 1).map(_.tokenType).contains("keyword"))
    }

    it("colours what it could read of a half-typed file rather than nothing") {
      // An unterminated string is a lexer error, and an editor sees that on every other
      // keystroke.
      assert(decode("val greeting = \"unterminated\nval n = 1\n").nonEmpty)
    }

    it("returns nothing for an empty document instead of failing") {
      assert(decode("").isEmpty)
    }
  }
}
