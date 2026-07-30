package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Future Module" section of docs/reference/stdlib.md
 * against its Japanese translation in docs/ja/reference/stdlib.md, which
 * trailed off partway through the section (`StdlibDocDriftSpec` can't catch
 * this: it only checks that documented `Class::method` calls exist
 * somewhere, not that a section is complete).
 */
class StdlibDocFutureModuleParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def subheadingsUnder(doc: String, heading: String): Seq[String] = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).filter(_.trim.startsWith("### "))
  }

  it("has the same number of Future Module subsections in English and Japanese") {
    val en = subheadingsUnder(read("docs/reference/stdlib.md"), "## Future Module")
    val ja = subheadingsUnder(read("docs/ja/reference/stdlib.md"), "## Future モジュール")
    assert(en.nonEmpty, "docs/reference/stdlib.md's Future Module section listed no subsections")
    assert(en.size == ja.size,
      s"docs/reference/stdlib.md has ${en.size} Future Module subsections but " +
      s"docs/ja/reference/stdlib.md has ${ja.size} — the section is missing or incomplete in Japanese")
  }
}
