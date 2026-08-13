package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Json Module" section of docs/reference/stdlib.md (and its
 * Japanese translation) against `onion.Json`'s public API. `Json::asObject`,
 * `Json::asArray`, and `Json::parseOrNull` are public static methods on the class
 * (see src/main/java/onion/Json.java) but were never mentioned in either reference,
 * making them undiscoverable without reading the Java source.
 */
class StdlibDocJsonAccessorParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).mkString("\n")
  }

  private val accessors = Seq("asObject", "asArray", "parseOrNull")

  it("mentions Json::asObject, Json::asArray, and Json::parseOrNull in the English Json Module section") {
    val en = sectionUnder(read("docs/reference/stdlib.md"), "## Json Module")
    accessors.foreach { name =>
      assert(en.contains(name),
        s"docs/reference/stdlib.md's Json Module section doesn't mention Json::$name, " +
        "a public onion.Json method")
    }
  }

  it("mentions Json::asObject, Json::asArray, and Json::parseOrNull in the Japanese Json Module section") {
    val ja = sectionUnder(read("docs/ja/reference/stdlib.md"), "## Json モジュール")
    accessors.foreach { name =>
      assert(ja.contains(name),
        s"docs/ja/reference/stdlib.md's Json モジュール section doesn't mention Json::$name, " +
        "a public onion.Json method")
    }
  }
}
