package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Timing Module" section of docs/reference/stdlib.md (and its
 * Japanese translation) against `onion.Timing`'s public API. Several public static
 * methods on the class (see src/main/java/onion/Timing.java) were never mentioned
 * in either reference, making them undiscoverable without reading the Java source.
 */
class StdlibDocTimingModuleParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).mkString("\n")
  }

  private val methods = Seq(
    "elapsedMillis", "formatMillis", "measureVoid", "sleepNanos"
  )

  it("mentions every public onion.Timing method in the English Timing Module section") {
    val en = sectionUnder(read("docs/reference/stdlib.md"), "## Timing Module")
    methods.foreach { name =>
      assert(en.contains(s"Timing::$name"),
        s"docs/reference/stdlib.md's Timing Module section doesn't mention Timing::$name, " +
        "a public onion.Timing method")
    }
  }

  it("mentions every public onion.Timing method in the Japanese Timing Module section") {
    val ja = sectionUnder(read("docs/ja/reference/stdlib.md"), "## Timing モジュール")
    methods.foreach { name =>
      assert(ja.contains(s"Timing::$name"),
        s"docs/ja/reference/stdlib.md's Timing モジュール section doesn't mention Timing::$name, " +
        "a public onion.Timing method")
    }
  }
}
