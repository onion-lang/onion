package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Maps Module" and "Sets Module" sections of docs/reference/stdlib.md
 * against their Japanese translations in docs/ja/reference/stdlib.md. Both Japanese sections
 * dropped several members during translation (Maps: newMap, getOrDefault, filterKeys,
 * filterValues, toList, forEach, merge; Sets: newSet, containsAll, forEach). Neither section
 * uses `###` subheadings in the Japanese doc, so `StdlibDocFutureModuleParitySpec`'s
 * subheading-count approach does not apply here — this compares the set of documented
 * `Maps::method` / `Sets::method` names instead, the same approach as
 * `StdlibDocStringsModuleParitySpec`.
 */
class StdlibDocMapsSetsModuleParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def methodsUnder(doc: String, heading: String, className: String): Set[String] = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    val section = lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).mkString("\n")
    s"""\\b$className::([a-zA-Z][A-Za-z0-9_]*)""".r
      .findAllMatchIn(section)
      .map(_.group(1))
      .toSet
  }

  it("documents the same Maps::* members in English and Japanese") {
    val en = methodsUnder(read("docs/reference/stdlib.md"), "## Maps Module", "Maps")
    val ja = methodsUnder(read("docs/ja/reference/stdlib.md"), "## Maps モジュール", "Maps")
    assert(en.nonEmpty, "docs/reference/stdlib.md's Maps Module section documented no Maps::* members")
    assert(en == ja,
      s"docs/reference/stdlib.md documents ${en.size} Maps::* members but " +
      s"docs/ja/reference/stdlib.md documents ${ja.size} — missing in Japanese: " +
      s"${(en -- ja).toSeq.sorted.mkString(", ")}")
  }

  it("documents the same Sets::* members in English and Japanese") {
    val en = methodsUnder(read("docs/reference/stdlib.md"), "## Sets Module", "Sets")
    val ja = methodsUnder(read("docs/ja/reference/stdlib.md"), "## Sets モジュール", "Sets")
    assert(en.nonEmpty, "docs/reference/stdlib.md's Sets Module section documented no Sets::* members")
    assert(en == ja,
      s"docs/reference/stdlib.md documents ${en.size} Sets::* members but " +
      s"docs/ja/reference/stdlib.md documents ${ja.size} — missing in Japanese: " +
      s"${(en -- ja).toSeq.sorted.mkString(", ")}")
  }
}
