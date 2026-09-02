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
  // contradicting the compiler's own error message. The marker whitelist itself
  // used to be written out separately in `TypingOutlinePass` and in the property
  // files' message text; both now read from `DeriveMarkers.all`, and this test
  // ties the doc's marker list to that same source so the two can't drift apart.

  private def section(doc: String, code: String): String = {
    val start = doc.indexOf(s"`$code`")
    assert(start >= 0, s"doc does not mention $code")
    val next = doc.indexOf("\n### `E", start + 1)
    if (next >= 0) doc.substring(start, next) else doc.substring(start)
  }

  private def checkDeriveMarkers(docPath: String): Unit = {
    val markers = onion.compiler.DeriveMarkers.all
    assert(markers.nonEmpty, "no derive! markers found in DeriveMarkers.all -- the scan has rotted")
    val e0063 = section(read(docPath), "E0063")
    val missing = markers.filterNot(e0063.contains)
    assert(missing.isEmpty, s"$docPath E0063 section does not mention marker(s): ${missing.mkString(", ")}")
  }

  it("the English E0063 section names every derive! marker the compiler supports") {
    checkDeriveMarkers("docs/reference/error-codes.md")
  }

  it("the Japanese E0063 section names every derive! marker the compiler supports") {
    checkDeriveMarkers("docs/ja/reference/error-codes.md")
  }

  // `shape name = <format>` supports `json`, `yaml` and `config` (`ShapeFormats.all`),
  // but the E0076 example comment in error-codes.md still listed only `json, yaml`
  // -- the same whitelist-drift mistake E0063 made, against a doc whose own
  // surrounding prose reads as authoritative. Ties the doc to `ShapeFormats.all` so
  // adding a fourth format can't leave the example stale again.

  private def checkShapeFormats(docPath: String): Unit = {
    val names = onion.compiler.ShapeFormats.all.map(_._1)
    assert(names.nonEmpty, "no shape formats found in ShapeFormats.all -- the scan has rotted")
    val e0076 = section(read(docPath), "E0076")
    val missing = names.filterNot(e0076.contains)
    assert(missing.isEmpty, s"$docPath E0076 section does not mention format(s): ${missing.mkString(", ")}")
  }

  it("the English E0076 section names every shape format the compiler supports") {
    checkShapeFormats("docs/reference/error-codes.md")
  }

  it("the Japanese E0076 section names every shape format the compiler supports") {
    checkShapeFormats("docs/ja/reference/error-codes.md")
  }

  // `record ... from re"..."`, `derive!`, and `tool` auto-CLI all restrict components to
  // the same eight-type scalar set, read from the single `ScalarConversions.all` table
  // (added for issue #349 after that list used to be written out five times). The E0061,
  // E0062, and E0081 sections each repeat the set as prose/comment instead of citing the
  // table, so nothing previously tied them to it -- the same drift risk already fixed for
  // E0063's markers and E0076's formats, one section over each time.

  private def checkScalarConversions(docPath: String, code: String): Unit = {
    val tags = onion.compiler.ScalarConversions.all.map(_.tag)
    assert(tags.nonEmpty, "no scalar conversions found in ScalarConversions.all -- the scan has rotted")
    val doc = section(read(docPath), code)
    val missing = tags.filterNot(doc.contains)
    assert(missing.isEmpty, s"$docPath $code section does not mention scalar type(s): ${missing.mkString(", ")}")
  }

  it("the English E0061 section names every scalar type the compiler supports") {
    checkScalarConversions("docs/reference/error-codes.md", "E0061")
  }

  it("the Japanese E0061 section names every scalar type the compiler supports") {
    checkScalarConversions("docs/ja/reference/error-codes.md", "E0061")
  }

  it("the English E0062 section names every scalar type the compiler supports") {
    checkScalarConversions("docs/reference/error-codes.md", "E0062")
  }

  it("the Japanese E0062 section names every scalar type the compiler supports") {
    checkScalarConversions("docs/ja/reference/error-codes.md", "E0062")
  }

  it("the English E0081 section names every scalar type the compiler supports") {
    checkScalarConversions("docs/reference/error-codes.md", "E0081")
  }

  it("the Japanese E0081 section names every scalar type the compiler supports") {
    checkScalarConversions("docs/ja/reference/error-codes.md", "E0081")
  }

  // `CapabilityCheckPass` used to hardcode its own private `parameterized: Set[Effect]`
  // (which effects a `requires { read(src) }`-style entry may take a parameter for) --
  // duplicated as prose in the E0079 section of both docs with nothing tying the two
  // together, the same drift risk already fixed for E0063's markers, E0076's formats,
  // and E0061/E0062/E0081's scalar types. `Effect.parameterized` is now the single
  // source of truth both the pass and this test read from.

  private def checkParameterizedEffects(docPath: String): Unit = {
    val names = onion.compiler.effects.Effect.parameterized.toSeq.sorted.map(_.name)
    assert(names.nonEmpty, "no parameterized effects found in Effect.parameterized -- the scan has rotted")
    val e0079 = section(read(docPath), "E0079")
    val missing = names.filterNot(e0079.contains)
    assert(missing.isEmpty, s"$docPath E0079 section does not mention parameterized effect(s): ${missing.mkString(", ")}")
  }

  it("the English E0079 section names every effect that takes a parameter argument") {
    checkParameterizedEffects("docs/reference/error-codes.md")
  }

  it("the Japanese E0079 section names every effect that takes a parameter argument") {
    checkParameterizedEffects("docs/ja/reference/error-codes.md")
  }

  // The E0079 section also states the *full* effect vocabulary (all nine names, not
  // just the ones that take a parameter) as a prose sentence in both docs. Nothing
  // tied that sentence to `Effect.all` -- the same drift risk `checkParameterizedEffects`
  // above already guards for the parameterized subset, one list over.

  private def checkEffectVocabulary(docPath: String): Unit = {
    val names = onion.compiler.effects.Effect.all.map(_.name)
    assert(names.nonEmpty, "no effects found in Effect.all -- the scan has rotted")
    val e0079 = section(read(docPath), "E0079")
    val missing = names.filterNot(e0079.contains)
    assert(missing.isEmpty, s"$docPath E0079 section does not mention effect(s): ${missing.mkString(", ")}")
  }

  it("the English E0079 section names every effect in the vocabulary") {
    checkEffectVocabulary("docs/reference/error-codes.md")
  }

  it("the Japanese E0079 section names every effect in the vocabulary") {
    checkEffectVocabulary("docs/ja/reference/error-codes.md")
  }
}
