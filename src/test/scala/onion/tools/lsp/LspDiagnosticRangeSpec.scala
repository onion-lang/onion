package onion.tools.lsp

import onion.compiler.Location
import org.scalatest.funspec.AnyFunSpec

/**
 * The editor used to widen a diagnostic to the identifier around the reported column by
 * scanning the line text, because the compiler only ever gave it a start position. That
 * heuristic is right for identifiers and wrong for everything else — an operator, a
 * literal, a keyword — and it cannot know where a construct ends.
 *
 * Now that a `Location` carries the token's extent, the range comes from the compiler and
 * the scan is only a fallback for locations that still have no span.
 */
class LspDiagnosticRangeSpec extends AnyFunSpec {

  private val service = new OnionTextDocumentService(null)

  private def range(content: String, loc: Location) =
    service.locationToRange(loc, content)

  describe("a location with a span") {
    it("uses the span rather than scanning for identifier boundaries") {
      val content = "val x = undefinedName\n"
      // "undefinedName" starts at column 9 and ends at column 21 (1-based, inclusive).
      val r = range(content, new Location(1, 9).withSpan(1, 21))
      assert(r.getStart.getCharacter == 8)
      assert(r.getEnd.getCharacter == 21)
    }

    it("covers a construct the identifier scan could not have found") {
      // A string literal: the old scan stops at the quote and would report an empty or
      // one-character range.
      val content = """val s = "hello world"""" + "\n"
      val r = range(content, new Location(1, 9).withSpan(1, 21))
      assert(r.getEnd.getCharacter - r.getStart.getCharacter == 13)
    }

    it("respects tab-expanded columns") {
      // JavaCC counts a tab as advancing to the next multiple of 8, so the reported
      // columns are expanded and must be mapped back to character indices.
      val content = "\tval x = abc\n"
      // After one tab (expanded 1..8), "val x = " occupies 9..16, "abc" is 17..19.
      val r = range(content, new Location(1, 17).withSpan(1, 19))
      assert(r.getStart.getCharacter == 9, s"start was ${r.getStart.getCharacter}")
      assert(r.getEnd.getCharacter == 12, s"end was ${r.getEnd.getCharacter}")
    }
  }

  describe("a location without a span") {
    it("still falls back to widening to the surrounding identifier") {
      val content = "val x = undefinedName\n"
      val r = range(content, new Location(1, 9))
      assert(r.getStart.getCharacter == 8)
      assert(r.getEnd.getCharacter == 21)
    }

    it("survives a null location") {
      val r = range("whatever\n", null)
      assert(r.getStart.getLine == 0)
    }
  }
}
