package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Colls Module" section of docs/reference/stdlib.md (and its
 * Japanese translation) against `onion.Colls`'s batching/aggregation helpers. The
 * "Modules at a glance" table already promises "chunked/windowed, sumBy/maxBy" for
 * Colls, but the Colls Module section itself never mentioned chunked, windowed,
 * sumBy, maxBy, or minBy — these aren't registered as compiler-level extension
 * methods anywhere else in the doc either, making them undiscoverable without
 * reading the Java source (see src/main/java/onion/Colls.java).
 */
class StdlibDocCollsBatchingAggregationParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).mkString("\n")
  }

  private val methods = Seq("chunked", "windowed", "sumBy", "maxBy", "minBy")

  it("mentions every Colls batching/aggregation helper in the English Colls Module section") {
    val en = sectionUnder(read("docs/reference/stdlib.md"), "## Colls Module")
    methods.foreach { name =>
      assert(en.contains(s"Colls::$name") || en.contains(s".$name("),
        s"docs/reference/stdlib.md's Colls Module section doesn't mention $name, " +
        "a public onion.Colls method")
    }
  }

  it("mentions every Colls batching/aggregation helper in the Japanese Colls Module section") {
    val ja = sectionUnder(read("docs/ja/reference/stdlib.md"), "## Colls モジュール")
    methods.foreach { name =>
      assert(ja.contains(s"Colls::$name") || ja.contains(s".$name("),
        s"docs/ja/reference/stdlib.md's Colls モジュール section doesn't mention $name, " +
        "a public onion.Colls method")
    }
  }
}
