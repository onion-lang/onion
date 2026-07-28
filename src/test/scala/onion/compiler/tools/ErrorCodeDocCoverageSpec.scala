package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the diagnostic-code reference (release prep for 0.10.6).
 *
 * `docs/reference/error-codes.md` explains the common codes in prose and then lists
 * every one in a table, so a code seen in a build log can always be looked up. 59 of
 * the 80 codes were missing when the table was added — including several introduced
 * during the previous week's work — because nothing tied the document to
 * `SemanticError`. This is that tie.
 */
class ErrorCodeDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private lazy val declared: Set[String] =
    """case object \w+ extends SemanticError\((\d+)\)""".r
      .findAllMatchIn(read("src/main/scala/onion/compiler/SemanticError.scala"))
      .map(m => f"E${m.group(1).toInt}%04d")
      .toSet

  private def check(path: String): Unit = {
    val doc = read(path)
    assert(declared.nonEmpty, "no SemanticError cases found — the scan has rotted")
    val missing = declared.filterNot(doc.contains).toSeq.sorted
    assert(missing.isEmpty, s"$path does not mention: ${missing.mkString(", ")}")

    // The reverse: a documented code that no longer exists sends readers looking for
    // a diagnostic they can never see.
    val documented = """`(E\d{4})`""".r.findAllMatchIn(doc).map(_.group(1)).toSet
    val stale = (documented -- declared).toSeq.sorted
    assert(stale.isEmpty, s"$path documents codes that no longer exist: ${stale.mkString(", ")}")
  }

  it("the English reference mentions every declared code, and no retired one") {
    check("docs/reference/error-codes.md")
  }

  it("the Japanese reference mentions every declared code, and no retired one") {
    check("docs/ja/reference/error-codes.md")
  }
}
