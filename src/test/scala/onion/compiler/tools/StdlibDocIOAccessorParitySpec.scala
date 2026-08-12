package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "IO Module" section of docs/reference/stdlib.md (and its
 * Japanese translation) against `onion.IO`'s public API. Several public static
 * methods on the class (see src/main/java/onion/IO.java) were never mentioned
 * in either reference, making them undiscoverable without reading the Java source.
 */
class StdlibDocIOAccessorParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).mkString("\n")
  }

  private val methods = Seq(
    "readLine", "readAll", "printf", "format", "eprint", "eprintln", "eprintf",
    "readInt", "readLong", "readDouble", "readBoolean",
    "tryReadInt", "tryReadDouble", "tryReadLong",
    "readLines", "eachLine", "printLines", "printAll", "flush", "newline", "clear"
  )

  it("mentions every public onion.IO method in the English IO Module section") {
    val en = sectionUnder(read("docs/reference/stdlib.md"), "## IO Module")
    methods.foreach { name =>
      assert(en.contains(s"IO::$name"),
        s"docs/reference/stdlib.md's IO Module section doesn't mention IO::$name, " +
        "a public onion.IO method")
    }
  }

  it("mentions every public onion.IO method in the Japanese IO Module section") {
    val ja = sectionUnder(read("docs/ja/reference/stdlib.md"), "## IO モジュール")
    methods.foreach { name =>
      assert(ja.contains(s"IO::$name"),
        s"docs/ja/reference/stdlib.md's IO モジュール section doesn't mention IO::$name, " +
        "a public onion.IO method")
    }
  }
}
