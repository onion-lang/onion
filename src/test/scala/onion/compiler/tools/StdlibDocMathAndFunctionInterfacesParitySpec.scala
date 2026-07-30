package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Math Module" and "Function Interfaces" sections of
 * docs/reference/stdlib.md against their Japanese translation in
 * docs/ja/reference/stdlib.md, which trails off partway through both sections
 * (`StdlibDocDriftSpec` can't catch this: it only checks that documented
 * `Class::method` calls exist somewhere, not that a section is complete).
 */
class StdlibDocMathAndFunctionInterfacesParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def subheadingsUnder(doc: String, heading: String): Seq[String] = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).filter(_.trim.startsWith("### "))
  }

  it("has the same number of Math Module subsections in English and Japanese") {
    val en = subheadingsUnder(read("docs/reference/stdlib.md"), "## Math Module")
    val ja = subheadingsUnder(read("docs/ja/reference/stdlib.md"), "## Math モジュール")
    assert(en.nonEmpty, "docs/reference/stdlib.md's Math Module section listed no subsections")
    assert(en.size == ja.size,
      s"docs/reference/stdlib.md has ${en.size} Math Module subsections but " +
      s"docs/ja/reference/stdlib.md has ${ja.size} — the section is missing or incomplete in Japanese")
  }

  it("has the same number of Function Interfaces subsections in English and Japanese") {
    val en = subheadingsUnder(read("docs/reference/stdlib.md"), "## Function Interfaces")
    val ja = subheadingsUnder(read("docs/ja/reference/stdlib.md"), "## 関数インターフェース")
    assert(en.nonEmpty, "docs/reference/stdlib.md's Function Interfaces section listed no subsections")
    assert(en.size == ja.size,
      s"docs/reference/stdlib.md has ${en.size} Function Interfaces subsections but " +
      s"docs/ja/reference/stdlib.md has ${ja.size} — the section is missing or incomplete in Japanese")
  }
}
