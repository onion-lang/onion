package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Http" section of docs/reference/stdlib.md (and its
 * Japanese translation) against `onion.Http`'s public API. `getResponse` and
 * `postResponse` (see src/main/java/onion/Http.java) return a `Response`
 * object carrying status/body/headers but were never mentioned in either
 * reference's Http section, making them undiscoverable without reading the
 * Java source.
 */
class StdlibDocHttpModuleParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith("## ")).mkString("\n")
  }

  private val methods = Seq("getResponse", "postResponse")

  it("mentions every public onion.Http Response-returning method in the English Http section") {
    val en = sectionUnder(read("docs/reference/stdlib.md"), "## Http")
    methods.foreach { name =>
      assert(en.contains(s"Http::$name"),
        s"docs/reference/stdlib.md's Http section doesn't mention Http::$name, " +
        "a public onion.Http method")
    }
  }

  it("mentions every public onion.Http Response-returning method in the Japanese Http section") {
    val ja = sectionUnder(read("docs/ja/reference/stdlib.md"), "## Http")
    methods.foreach { name =>
      assert(ja.contains(s"Http::$name"),
        s"docs/ja/reference/stdlib.md's Http section doesn't mention Http::$name, " +
        "a public onion.Http method")
    }
  }
}
