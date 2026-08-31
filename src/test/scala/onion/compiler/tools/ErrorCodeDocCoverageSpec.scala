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

  // `derive!` has supported both `Json` and `Yaml` markers for a while (see
  // `error.semantic.recordDeriveUnknownMarker`), but the E0063 prose in
  // error-codes.md still claimed `Json` was the only one — actively
  // contradicting the compiler's own error message. Ties the doc's marker
  // list to the property file so the two can't drift apart silently again.

  private def section(doc: String, code: String): String = {
    val start = doc.indexOf(s"`$code`")
    assert(start >= 0, s"doc does not mention $code")
    val next = doc.indexOf("\n### `E", start + 1)
    if (next >= 0) doc.substring(start, next) else doc.substring(start)
  }

  private def supportedMarkers(propsPath: String): Set[String] = {
    val props = read(propsPath)
    val line = props.linesIterator
      .find(_.startsWith("error.semantic.recordDeriveUnknownMarker="))
      .getOrElse(fail(s"recordDeriveUnknownMarker key not found in $propsPath"))
    val tail = """(?:Supported markers|サポートしているマーカー): (.*)$""".r
      .findFirstMatchIn(line)
      .map(_.group(1))
      .getOrElse(fail(s"unexpected recordDeriveUnknownMarker format in $propsPath: $line"))
    tail
      .replaceAll("[。.]\\s*$", "")
      .split(",")
      .map(_.trim)
      .filter(_.nonEmpty)
      .toSet
  }

  private def checkDeriveMarkers(docPath: String, propsPath: String): Unit = {
    val markers = supportedMarkers(propsPath)
    assert(markers.nonEmpty, s"no derive! markers parsed from $propsPath — the scan has rotted")
    val e0063 = section(read(docPath), "E0063")
    val missing = markers.filterNot(e0063.contains).toSeq.sorted
    assert(missing.isEmpty, s"$docPath E0063 section does not mention marker(s): ${missing.mkString(", ")}")
  }

  it("the English E0063 section names every derive! marker the compiler supports") {
    checkDeriveMarkers("docs/reference/error-codes.md", "src/main/resources/errorMessage.properties")
  }

  it("the Japanese E0063 section names every derive! marker the compiler supports") {
    checkDeriveMarkers("docs/ja/reference/error-codes.md", "src/main/resources/errorMessage_ja.properties")
  }
}
