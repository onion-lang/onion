package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Archive" section of docs/reference/stdlib.md (and its Japanese
 * translation) against `onion.Archive`'s public API. `gunzipFile` (see
 * src/main/java/onion/Archive.java) sits right next to its documented sibling
 * `gzipFile` but was never mentioned in either reference, making it undiscoverable
 * without reading the Java source.
 */
class StdlibDocArchiveModuleParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).mkString("\n")
  }

  private val methods = Seq(
    "zip", "zipDir", "entries", "unzip", "gzip", "gunzip", "gzipFile", "gunzipFile"
  )

  it("mentions every public onion.Archive method in the English Archive section") {
    val en = sectionUnder(read("docs/reference/stdlib.md"), "## Archive")
    methods.foreach { name =>
      assert(en.contains(s"Archive::$name"),
        s"docs/reference/stdlib.md's Archive section doesn't mention Archive::$name, " +
        "a public onion.Archive method")
    }
  }

  it("mentions every public onion.Archive method in the Japanese Archive section") {
    val ja = sectionUnder(read("docs/ja/reference/stdlib.md"), "## Archive")
    methods.foreach { name =>
      assert(ja.contains(s"Archive::$name"),
        s"docs/ja/reference/stdlib.md's Archive section doesn't mention Archive::$name, " +
        "a public onion.Archive method")
    }
  }
}
