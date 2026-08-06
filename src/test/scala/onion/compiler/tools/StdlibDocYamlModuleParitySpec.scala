package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Yaml Module" section of docs/reference/stdlib.md against its
 * Japanese translation in docs/ja/reference/stdlib.md, which collapsed the English
 * section's four subsections (`Yaml::parse`, `Yaml::stringify`, round-trip guarantee,
 * `derive!(Yaml)` usage) into a single short paragraph, dropping the round-trip
 * guarantee statement and the `derive!(Yaml)` code examples entirely. Same approach
 * as `StdlibDocFutureModuleParitySpec`.
 */
class StdlibDocYamlModuleParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def subheadingsUnder(doc: String, heading: String): Seq[String] = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).filter(_.trim.startsWith("### "))
  }

  it("has the same number of Yaml Module subsections in English and Japanese") {
    val en = subheadingsUnder(read("docs/reference/stdlib.md"), "## Yaml Module")
    val ja = subheadingsUnder(read("docs/ja/reference/stdlib.md"), "## Yaml モジュール")
    assert(en.nonEmpty, "docs/reference/stdlib.md's Yaml Module section listed no subsections")
    assert(en.size == ja.size,
      s"docs/reference/stdlib.md has ${en.size} Yaml Module subsections but " +
      s"docs/ja/reference/stdlib.md has ${ja.size} — the section is missing or incomplete in Japanese")
  }

  it("mentions derive!(Yaml) usage in the Japanese Yaml Module section") {
    val ja = read("docs/ja/reference/stdlib.md")
    val lines = ja.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == "## Yaml モジュール")
    assert(start >= 0, "could not find '## Yaml モジュール' heading")
    val section = lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).mkString("\n")
    assert(section.contains("derive!(Yaml)"),
      "docs/ja/reference/stdlib.md's Yaml Module section no longer shows a derive!(Yaml) example")
    assert(section.contains("ServerConfig") || section.contains("derive!(Json, Yaml)"),
      "docs/ja/reference/stdlib.md's Yaml Module section is missing the derive!(Yaml) code example")
  }
}
