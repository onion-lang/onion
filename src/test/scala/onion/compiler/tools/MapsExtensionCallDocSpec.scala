package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * `onion.Maps`'s own javadoc and docs/reference/stdlib.md only ever spell its members as
 * `Maps::name(m, ...)` static calls, but `keys`, `values` and `getOrDefault` are also real
 * extension methods on `Map` (`m.keys()`, `m.values()`, `m.getOrDefault("x", 0)` all compile
 * and run -- verified against a `Map` literal), left undocumented in both languages.
 */
class MapsExtensionCallDocSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def mapsSection(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toIndexedSeq
    val start = lines.indexWhere(_.contains(heading))
    assert(start >= 0, s"""could not find a "$heading" heading -- the scan has rotted""")
    val rest = lines.drop(start + 1)
    val end = rest.indexWhere(_.startsWith("## "))
    (if (end < 0) rest else rest.take(end)).mkString("\n")
  }

  private val extensionCalls = Set("m.keys(", "m.values(", "m.getOrDefault(")

  it("docs/reference/stdlib.md's Maps Module section documents m.keys()/m.values()/m.getOrDefault() as extension calls") {
    val doc = mapsSection(read("docs/reference/stdlib.md"), "## Maps Module")
    val missing = extensionCalls.filterNot(doc.contains)
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md's Maps Module section is missing extension-call spellings: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md's Maps section documents m.keys()/m.values()/m.getOrDefault() as extension calls") {
    val doc = mapsSection(read("docs/ja/reference/stdlib.md"), "## Maps モジュール")
    val missing = extensionCalls.filterNot(doc.contains)
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md's Maps section is missing extension-call spellings: ${missing.toSeq.sorted.mkString(", ")}")
  }
}
