package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Files Module" section of docs/reference/stdlib.md (and its
 * Japanese translation) against `onion.Files`'s public API. Several public static
 * methods on the class (see src/main/java/onion/Files.java) were never mentioned
 * in either reference, making them undiscoverable without reading the Java source.
 */
class StdlibDocFilesModuleParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).mkString("\n")
  }

  private val methods = Seq(
    "isFile", "isDirectory", "mkdirs", "listFiles", "size",
    "getAbsolutePath", "copy", "move", "copyDir", "writeLines", "appendText"
  )

  it("mentions every public onion.Files method in the English Files Module section") {
    val en = sectionUnder(read("docs/reference/stdlib.md"), "## Files Module")
    methods.foreach { name =>
      assert(en.contains(s"Files::$name"),
        s"docs/reference/stdlib.md's Files Module section doesn't mention Files::$name, " +
        "a public onion.Files method")
    }
  }

  it("mentions every public onion.Files method in the Japanese Files Module section") {
    val ja = sectionUnder(read("docs/ja/reference/stdlib.md"), "## Files モジュール")
    methods.foreach { name =>
      assert(ja.contains(s"Files::$name"),
        s"docs/ja/reference/stdlib.md's Files モジュール section doesn't mention Files::$name, " +
        "a public onion.Files method")
    }
  }
}
