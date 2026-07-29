package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for CLAUDE.md's "Common Syntax Mistakes (IMPORTANT)" chapter against
 * its Japanese translation in docs/ja/CLAUDE_ja.md ("よくある構文ミス（重要）").
 * A whole subsection (### heading) dropped during translation is easy to miss by eye,
 * so this asserts the subsection headings under that chapter match 1:1, in order.
 */
class ClaudeMdSyntaxMistakesSectionParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def subsectionsUnder(doc: String, chapterHeading: String): Seq[String] = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == chapterHeading)
    assert(start >= 0, s"could not find heading '$chapterHeading' — the scan has rotted")
    lines.drop(start + 1)
      .takeWhile(!_.trim.startsWith("## "))
      .filter(_.trim.startsWith("### "))
      .map(_.trim.stripPrefix("### "))
  }

  it("has the same Common Syntax Mistakes subsections in English and Japanese, in order") {
    val en = subsectionsUnder(read("CLAUDE.md"), "## Common Syntax Mistakes (IMPORTANT)")
    val ja = subsectionsUnder(read("docs/ja/CLAUDE_ja.md"), "## よくある構文ミス（重要）")
    assert(en.nonEmpty, "CLAUDE.md's Common Syntax Mistakes chapter listed no subsections")
    assert(en.size == ja.size,
      s"CLAUDE.md has ${en.size} Common Syntax Mistakes subsections but docs/ja/CLAUDE_ja.md has ${ja.size} " +
      s"— a subsection was likely dropped during translation.\nEN: $en\nJA: $ja")
  }
}
