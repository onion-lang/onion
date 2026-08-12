package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Strings Module" section of docs/reference/stdlib.md (and its
 * Japanese translation) against `onion.Strings`'s public API. Several public static
 * methods on the class (see src/main/java/onion/Strings.java) were never mentioned
 * in either reference, making them undiscoverable without reading the Java source.
 */
class StdlibDocStringsAccessorParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).mkString("\n")
  }

  private val methods = Seq(
    "splitRegex", "isEmpty", "isBlank", "substring",
    "indexOf", "lastIndexOf", "lines", "reverse", "decapitalize"
  )

  it("mentions every public onion.Strings method in the English Strings Module section") {
    val en = sectionUnder(read("docs/reference/stdlib.md"), "## Strings Module")
    methods.foreach { name =>
      assert(en.contains(s"Strings::$name"),
        s"docs/reference/stdlib.md's Strings Module section doesn't mention Strings::$name, " +
        "a public onion.Strings method")
    }
  }

  it("mentions every public onion.Strings method in the Japanese Strings Module section") {
    val ja = sectionUnder(read("docs/ja/reference/stdlib.md"), "## Strings モジュール")
    methods.foreach { name =>
      assert(ja.contains(s"Strings::$name"),
        s"docs/ja/reference/stdlib.md's Strings モジュール section doesn't mention Strings::$name, " +
        "a public onion.Strings method")
    }
  }
}
