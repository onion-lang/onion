package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * `docs/guide/control-flow.md` has a "### Mixing with Regular Expressions" section
 * under "Do Notation (Monadic Composition)" whose lead-in claims "You can mix
 * bindings with regular expressions and declarations" — but its example contains
 * no regex at all; it actually demonstrates mixing a `<-` bind with a local `val`
 * declaration and a side-effecting statement. There is no such feature (a do-block
 * bind against a regex) anywhere in the compiler (`do[...]` desugars purely to
 * `.bind(...)`/`successful(...)` chains — see `Rewriting.scala`), so this is a
 * stale/mislabeled heading, not a missing feature to implement. Guards that the
 * heading and lead-in for that section actually describe its content (local
 * declarations + side effects), not regular expressions.
 */
class DoNotationDocRegexHeadingMismatchSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private val doc = read("docs/guide/control-flow.md")

  // Identifies the section by its actual content (the "Local declaration" comment
  // in the do-block example), independent of whatever the heading currently says.
  private val markerLine = "// Local declaration"

  describe("docs/guide/control-flow.md do-notation section mixing binds with local declarations") {
    it("has a heading/lead-in that does not claim to be about regular expressions") {
      val lines = doc.linesIterator.toSeq
      val markerIdx = lines.indexWhere(_.contains(markerLine))
      assert(markerIdx >= 0, s"could not find the '$markerLine' example — the scan has rotted")

      val precedingHeadingIdx = lines.lastIndexWhere(_.trim.startsWith("### "), markerIdx)
      assert(precedingHeadingIdx >= 0, "could not find a heading above the local-declaration example")
      val heading = lines(precedingHeadingIdx)
      val leadIn = lines.slice(precedingHeadingIdx + 1, markerIdx).mkString("\n")

      assert(!heading.toLowerCase.contains("regular expression") && !heading.toLowerCase.contains("regex"),
        s"heading '$heading' claims this section is about regular expressions, but its example " +
        "only mixes a bind with a local `val` declaration and a side effect -- no regex involved")
      assert(!leadIn.toLowerCase.contains("regular expression") && !leadIn.toLowerCase.contains("regex"),
        s"lead-in text under '$heading' claims regular expressions but the example has none:\n$leadIn")
    }

    it("the example under that heading contains no regex literal") {
      val lines = doc.linesIterator.toSeq
      val markerIdx = lines.indexWhere(_.contains(markerLine))
      assert(markerIdx >= 0, s"could not find the '$markerLine' example — the scan has rotted")
      val fenceStart = lines.lastIndexWhere(_.trim.startsWith("```"), markerIdx)
      val fenceEnd = lines.indexWhere(_.trim.startsWith("```"), markerIdx)
      assert(fenceStart >= 0 && fenceEnd >= 0, "could not find the fenced code block around the example")
      val body = lines.slice(fenceStart + 1, fenceEnd).mkString("\n")
      assert(!body.contains("re\""), s"example claimed to mix regex but contains no re\"...\" literal:\n$body")
      assert(body.contains("val ") && body.contains("<-"),
        s"expected the example to mix a local `val` declaration with a `<-` bind:\n$body")
    }
  }
}
