package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "When to reach for `Shapes` directly" section of
 * docs/guide/shapes.md (and its Japanese translation) against `onion.Shapes`'s public
 * API. `Shapes::regex` and `Shapes::json` are mentioned, but `Shapes::config` and
 * `Shapes::yaml` (see src/main/java/onion/Shapes.java) — the hand-written equivalents
 * of `shape name = config` / `shape name = yaml` — are not, making them undiscoverable
 * without reading the Java source.
 */
class ShapesGuideDirectApiParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).mkString("\n")
  }

  private val factories = Seq("regex", "json", "config", "yaml")

  it("mentions every public onion.Shapes factory in the English guide's direct-API section") {
    val en = sectionUnder(read("docs/guide/shapes.md"), "## When to reach for `Shapes` directly")
    factories.foreach { name =>
      assert(en.contains(s"Shapes::$name"),
        s"docs/guide/shapes.md's direct-API section doesn't mention Shapes::$name, " +
        "a public onion.Shapes factory")
    }
  }

  it("mentions every public onion.Shapes factory in the Japanese guide's direct-API section") {
    val ja = sectionUnder(read("docs/ja/guide/shapes.md"), "## `Shapes` を直接使うとき")
    factories.foreach { name =>
      assert(ja.contains(s"Shapes::$name"),
        s"docs/ja/guide/shapes.md's direct-API section doesn't mention Shapes::$name, " +
        "a public onion.Shapes factory")
    }
  }
}
