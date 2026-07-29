package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Wrapper Classes" and "Common Java Classes" sections of
 * docs/reference/stdlib.md against their Japanese translation in
 * docs/ja/reference/stdlib.md, which is missing both top-level sections entirely
 * (`StdlibDocDriftSpec` can't catch this: `JInteger`/`String`/`StringBuilder`/... are
 * deliberately excluded there as non-`onion.*` aliases and JDK types).
 */
class StdlibDocWrapperAndCommonJavaClassesParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def subheadingsUnder(doc: String, heading: String): Seq[String] = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).filter(_.trim.startsWith("### "))
  }

  it("has the same number of Wrapper Classes subsections in English and Japanese") {
    val en = subheadingsUnder(read("docs/reference/stdlib.md"), "## Wrapper Classes")
    val ja = subheadingsUnder(read("docs/ja/reference/stdlib.md"), "## ラッパークラス")
    assert(en.nonEmpty, "docs/reference/stdlib.md's Wrapper Classes section listed no subsections")
    assert(en.size == ja.size,
      s"docs/reference/stdlib.md has ${en.size} Wrapper Classes subsections but " +
      s"docs/ja/reference/stdlib.md has ${ja.size} — the section is missing or incomplete in Japanese")
  }

  it("has the same number of Common Java Classes subsections in English and Japanese") {
    val en = subheadingsUnder(read("docs/reference/stdlib.md"), "## Common Java Classes")
    val ja = subheadingsUnder(read("docs/ja/reference/stdlib.md"), "## よく使うJavaクラス")
    assert(en.nonEmpty, "docs/reference/stdlib.md's Common Java Classes section listed no subsections")
    assert(en.size == ja.size,
      s"docs/reference/stdlib.md has ${en.size} Common Java Classes subsections but " +
      s"docs/ja/reference/stdlib.md has ${ja.size} — the section is missing or incomplete in Japanese")
  }
}
