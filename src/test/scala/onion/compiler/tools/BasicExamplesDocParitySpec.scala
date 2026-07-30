package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for docs/examples/basic.md against its Japanese translation,
 * docs/ja/examples/basic.md, which was missing the "CSV Processing" and
 * "Regex Log Parsing" sections entirely.
 */
class BasicExamplesDocParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def apiTokens(doc: String): Set[String] =
    """(Csv|Regex|JInt)::\w+""".r.findAllMatchIn(doc).map(_.group(0)).toSet

  it("mentions the same stdlib API calls as the English basic examples doc") {
    val en = apiTokens(read("docs/examples/basic.md"))
    val ja = apiTokens(read("docs/ja/examples/basic.md"))
    assert(en.nonEmpty, "docs/examples/basic.md listed no Csv/Regex/JInt calls — the scan has rotted")
    val missing = en -- ja
    assert(missing.isEmpty,
      s"docs/ja/examples/basic.md is missing ${missing.size} stdlib call(s) present in English: " +
      missing.toSeq.sorted.mkString(", "))
  }
}
