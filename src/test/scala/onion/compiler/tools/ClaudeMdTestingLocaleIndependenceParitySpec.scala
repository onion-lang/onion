package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for CLAUDE.md's "Testing" section against its Japanese translation in
 * docs/ja/CLAUDE_ja.md ("テスト"). The "Locale-independence (IMPORTANT)" paragraph warns
 * that error messages are bilingual and CI runs in an English locale — a warning a
 * Japanese-reading contributor needs just as much as an English one, so its absence from
 * the translation is easy to miss by eye but costly to actually miss.
 */
class ClaudeMdTestingLocaleIndependenceParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def hasLocaleIndependenceParagraph(doc: String, chapterHeading: String, marker: String): Boolean = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == chapterHeading)
    assert(start >= 0, s"could not find heading '$chapterHeading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).exists(_.trim.startsWith(marker))
  }

  it("documents the Locale-independence warning in both English and Japanese") {
    val en = hasLocaleIndependenceParagraph(read("CLAUDE.md"), "## Testing", "**Locale-independence (IMPORTANT):**")
    val ja = hasLocaleIndependenceParagraph(read("docs/ja/CLAUDE_ja.md"), "## テスト", "**ロケール非依存性（重要）:**")
    assert(en, "CLAUDE.md's Testing section lost its Locale-independence paragraph")
    assert(ja, "docs/ja/CLAUDE_ja.md's Testing section is missing the Locale-independence paragraph " +
      "present in CLAUDE.md — it was likely dropped during translation")
  }
}
