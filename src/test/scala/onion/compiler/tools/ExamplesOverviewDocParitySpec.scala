package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for docs/examples/overview.md's "Example Index" table against its
 * Japanese counterpart, docs/ja/examples/index.md. The latter used to be a short,
 * stale stub (38 lines) that predated most of the entries in the English table and
 * carried none of the per-example rows — nothing tied the two together, so the gap
 * only grew as new examples were documented in English.
 */
class ExamplesOverviewDocParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def onFileNames(doc: String): Set[String] =
    """`(\w+\.on)`""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  it("references the same set of run/*.on examples as the English overview") {
    val en = onFileNames(read("docs/examples/overview.md"))
    val ja = onFileNames(read("docs/ja/examples/index.md"))
    assert(en.nonEmpty, "docs/examples/overview.md listed no .on examples — the scan has rotted")
    val missing = en -- ja
    assert(missing.isEmpty,
      s"docs/ja/examples/index.md is missing ${missing.size} example(s) present in English: " +
      missing.toSeq.sorted.mkString(", "))
  }
}
