package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for CLAUDE.md's "Records" mistakes table (under "Common Syntax Mistakes")
 * against its Japanese translation in docs/ja/CLAUDE_ja.md ("レコード" under
 * "よくある構文ミス"). Both tables warn a reader coming from Kotlin's `data class` and
 * Scala's `case class` that Onion's `record` takes no `val`/`var` in the component list —
 * a mistake just as easy to make reading the Japanese docs as the English ones, so its
 * absence from the translation is easy to miss by eye but costly to actually miss.
 */
class ClaudeMdRecordsMistakesTableParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def tableRowCount(doc: String, chapterHeading: String): Int = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == chapterHeading)
    assert(start >= 0, s"could not find heading '$chapterHeading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("### ")).count(_.trim.startsWith("|")) - 2 // header + separator
  }

  it("keeps the same number of Records mistake rows in English and Japanese") {
    val en = tableRowCount(read("CLAUDE.md"), "### Records")
    val ja = tableRowCount(read("docs/ja/CLAUDE_ja.md"), "### レコード")
    assert(en == ja,
      s"CLAUDE.md's Records table has $en rows but docs/ja/CLAUDE_ja.md's has $ja — " +
        "a row was likely added to one and not translated into the other")
  }

  it("documents the Kotlin data class and Scala case class mistakes in both languages") {
    val en = read("CLAUDE.md")
    val ja = read("docs/ja/CLAUDE_ja.md")
    assert(en.contains("data class Point"), "CLAUDE.md lost the Kotlin `data class` mistake row")
    assert(en.contains("case class Point"), "CLAUDE.md lost the Scala `case class` mistake row")
    assert(ja.contains("data class Point"),
      "docs/ja/CLAUDE_ja.md is missing the Kotlin `data class` mistake row present in CLAUDE.md")
    assert(ja.contains("case class Point"),
      "docs/ja/CLAUDE_ja.md is missing the Scala `case class` mistake row present in CLAUDE.md")
  }
}
