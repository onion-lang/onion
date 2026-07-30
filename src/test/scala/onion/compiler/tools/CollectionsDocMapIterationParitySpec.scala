package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Map Iteration" section of docs/guide/collections.md
 * against its Japanese translation in docs/ja/guide/collections.md, which
 * dropped the generic type arguments on `Map.Entry` and left the intro
 * sentence untranslated when the two files were added together.
 */
class CollectionsDocMapIterationParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def codeFenceUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    val afterHeading = lines.drop(start + 1)
    val fenceStart = afterHeading.indexWhere(_.trim.startsWith("```"))
    assert(fenceStart >= 0, s"no code fence found under '$heading'")
    val body = afterHeading.drop(fenceStart + 1)
    body.takeWhile(!_.trim.startsWith("```")).mkString("\n")
  }

  it("keeps the Map.Entry generic type arguments in the Japanese translation") {
    val en = codeFenceUnder(read("docs/guide/collections.md"), "## Map Iteration")
    val ja = codeFenceUnder(read("docs/ja/guide/collections.md"), "## マップのイテレーション")
    assert(en.contains("Map.Entry[String, Integer]"),
      "docs/guide/collections.md's Map Iteration sample no longer uses Map.Entry[String, Integer] — the scan has rotted")
    assert(ja.contains("Map.Entry[String, Integer]"),
      "docs/ja/guide/collections.md's Map Iteration sample is missing the Map.Entry[String, Integer] generic " +
      "type arguments present in the English version")
  }
}
