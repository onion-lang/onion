package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Strings Module" section of docs/reference/stdlib.md against its
 * Japanese translation in docs/ja/reference/stdlib.md, which dropped the first two code
 * blocks (split/join/case/padding and startsWith/endsWith/contains/isEmpty/isBlank/
 * substring/indexOf/lastIndexOf/lines/reverse) during translation. The section has no
 * `###` subheadings, so `StdlibDocFutureModuleParitySpec`'s subheading-count approach
 * does not apply here — this compares the set of documented `Strings::method` names
 * instead.
 */
class StdlibDocStringsModuleParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def stringsMethodsUnder(doc: String, heading: String): Set[String] = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    val section = lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).mkString("\n")
    """\bStrings::([a-zA-Z][A-Za-z0-9_]*)""".r
      .findAllMatchIn(section)
      .map(_.group(1))
      .toSet
  }

  it("documents the same Strings::* members in English and Japanese") {
    val en = stringsMethodsUnder(read("docs/reference/stdlib.md"), "## Strings Module")
    val ja = stringsMethodsUnder(read("docs/ja/reference/stdlib.md"), "## Strings モジュール")
    assert(en.nonEmpty, "docs/reference/stdlib.md's Strings Module section documented no Strings::* members")
    assert(en == ja,
      s"docs/reference/stdlib.md documents ${en.size} Strings::* members but " +
      s"docs/ja/reference/stdlib.md documents ${ja.size} — missing in Japanese: " +
      s"${(en -- ja).toSeq.sorted.mkString(", ")}")
  }
}
