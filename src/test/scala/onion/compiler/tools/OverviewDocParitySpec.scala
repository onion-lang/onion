package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for docs/guide/overview.md against its Japanese translation,
 * docs/ja/guide/overview.md, which was missing the "JVM Target" and "Java
 * Interoperability" subsections of "Language Characteristics" and the entire
 * "Syntax Highlights" section (five subsections) — only the first three
 * Language Characteristics subsections had ever been translated.
 */
class OverviewDocParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def subheadingsUnder(doc: String, heading: String): Seq[String] = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).filter(_.trim.startsWith("### "))
  }

  it("has the same number of Language Characteristics subsections in English and Japanese") {
    val en = subheadingsUnder(read("docs/guide/overview.md"), "## Language Characteristics")
    val ja = subheadingsUnder(read("docs/ja/guide/overview.md"), "## 主な特徴")
    assert(en.nonEmpty, "docs/guide/overview.md's Language Characteristics section listed no subsections")
    assert(en.size == ja.size,
      s"docs/guide/overview.md has ${en.size} Language Characteristics subsections but " +
      s"docs/ja/guide/overview.md has ${ja.size} — the section is missing or incomplete in Japanese")
  }

  it("has the same number of Syntax Highlights subsections in English and Japanese") {
    val en = subheadingsUnder(read("docs/guide/overview.md"), "## Syntax Highlights")
    val ja = subheadingsUnder(read("docs/ja/guide/overview.md"), "## 構文のハイライト")
    assert(en.nonEmpty, "docs/guide/overview.md's Syntax Highlights section listed no subsections")
    assert(en.size == ja.size,
      s"docs/guide/overview.md has ${en.size} Syntax Highlights subsections but " +
      s"docs/ja/guide/overview.md has ${ja.size} — the section is missing or incomplete in Japanese")
  }
}
