package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the "Modules at a glance" summary table in `docs/reference/stdlib.md`
 * (and its Japanese "モジュール一覧" counterpart). `Range`, `FileResource`, `HttpResource`
 * and `Lossless` each have their own dedicated `## ClassName` section further down both
 * files -- covered by `RangeDocCoverageSpec`, `FileResourceDocCoverageSpec` and
 * `HttpResourceDocCoverageSpec` -- but none of the four appear in the summary table, so a
 * reader skimming just the table never learns these classes exist. The same omission
 * propagates to `CLAUDE.md`'s "Standard Library" list and its Japanese twin, which
 * `StdlibDocDriftSpec` derives from this very table.
 */
class ModulesAtAGlanceCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def glanceTable(doc: String, marker: String, stopAt: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.contains(marker))
    assert(start >= 0, s"doc has no '$marker' section -- the scan has rotted")
    lines.drop(start + 1).takeWhile(!_.trim.startsWith(stopAt)).mkString("\n")
  }

  private val classes = Seq("Range", "FileResource", "HttpResource", "Lossless")

  it("docs/reference/stdlib.md's Modules at a glance table mentions Range, FileResource, HttpResource and Lossless") {
    val table = glanceTable(read("docs/reference/stdlib.md"), "## Modules at a glance", "Most helpers")
    classes.foreach { name =>
      assert(table.contains(s"`$name`"), s"'Modules at a glance' table doesn't mention `$name`")
    }
  }

  it("docs/ja/reference/stdlib.md's モジュール一覧 table mentions Range, FileResource, HttpResource and Lossless") {
    val table = glanceTable(read("docs/ja/reference/stdlib.md"), "## モジュール一覧", "ほとんど")
    classes.foreach { name =>
      assert(table.contains(s"`$name`"), s"'モジュール一覧' table doesn't mention `$name`")
    }
  }
}
