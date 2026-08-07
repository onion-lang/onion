package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Json Module" section of docs/reference/stdlib.md against its
 * Japanese translation in docs/ja/reference/stdlib.md. The English section documents
 * the navigable `Json::value(text)` wrapper (`Value`, indexable with `[]` and read
 * with `asString`/`asInt`/.../`asBoolean`), but the Japanese section never mentions
 * it, leaving `Json::value` and its `[]`/`as*` API undiscoverable in Japanese.
 */
class StdlibDocJsonModuleParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).mkString("\n")
  }

  it("mentions the Json::value navigable wrapper in the Japanese Json Module section") {
    val ja = sectionUnder(read("docs/ja/reference/stdlib.md"), "## Json モジュール")
    assert(ja.contains("Json::value"),
      "docs/ja/reference/stdlib.md's Json Module section doesn't mention Json::value, " +
      "the navigable JSON wrapper documented in the English reference")
  }

  it("shows the [] indexing and as* accessor usage in the Japanese Json Module section") {
    val ja = sectionUnder(read("docs/ja/reference/stdlib.md"), "## Json モジュール")
    assert(ja.contains("asString()"),
      "docs/ja/reference/stdlib.md's Json Module section is missing the Value.asString() " +
      "example that the English reference shows for Json::value")
  }
}
