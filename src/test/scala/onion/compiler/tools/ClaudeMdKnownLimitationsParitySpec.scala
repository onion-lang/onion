package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for CLAUDE.md's "Known Limitations" section against its Japanese
 * translation in docs/ja/CLAUDE_ja.md ("既知の制限"). A bullet dropped during
 * translation is easy to miss by eye, so this asserts the bullet counts match.
 */
class ClaudeMdKnownLimitationsParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def bulletsUnder(doc: String, heading: String): Seq[String] = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).filter(_.trim.startsWith("- "))
  }

  it("has the same number of Known Limitations bullets in English and Japanese") {
    val en = bulletsUnder(read("CLAUDE.md"), "## Known Limitations")
    val ja = bulletsUnder(read("docs/ja/CLAUDE_ja.md"), "## 既知の制限")
    assert(en.nonEmpty, "CLAUDE.md's Known Limitations section listed no bullets")
    assert(en.size == ja.size,
      s"CLAUDE.md has ${en.size} Known Limitations bullets but docs/ja/CLAUDE_ja.md has ${ja.size} " +
      "— a bullet was likely dropped during translation")
  }
}
