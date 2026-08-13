package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Iterables Module" section of docs/reference/stdlib.md (and its
 * Japanese translation) against `onion.Iterables`'s public API. Several public static
 * methods on the class (see src/main/java/onion/Iterables.java) were never mentioned
 * in either reference's Iterables Module section, making them undiscoverable without
 * reading the Java source.
 */
class StdlibDocIterablesModuleParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).mkString("\n")
  }

  private val methods = Seq(
    "map", "mapMap", "toList", "filter", "foldl", "reduce",
    "exists", "forAll", "listOf", "newList", "first", "last",
    "reverse", "take", "drop", "sort"
  )

  it("mentions every public onion.Iterables method in the English Iterables Module section") {
    val en = sectionUnder(read("docs/reference/stdlib.md"), "## Iterables Module")
    methods.foreach { name =>
      assert(en.contains(s"Iterables::$name"),
        s"docs/reference/stdlib.md's Iterables Module section doesn't mention Iterables::$name, " +
        "a public onion.Iterables method")
    }
  }

  it("mentions every public onion.Iterables method in the Japanese Iterables Module section") {
    val ja = sectionUnder(read("docs/ja/reference/stdlib.md"), "## Iterables モジュール")
    methods.foreach { name =>
      assert(ja.contains(s"Iterables::$name"),
        s"docs/ja/reference/stdlib.md's Iterables モジュール section doesn't mention Iterables::$name, " +
        "a public onion.Iterables method")
    }
  }
}
