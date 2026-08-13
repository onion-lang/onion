package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the warning-code reference, mirroring ErrorCodeDocCoverageSpec.
 *
 * `E0xxx` error codes get a full reference table in docs/reference/error-codes.md,
 * guarded against drift. `W0xxx` warning codes (`WarningCategory` in Warning.scala,
 * surfaced via `--Wno`) had no such table and no guard. This is that tie.
 */
class WarningCodeDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private lazy val declared: Set[String] =
    """extends WarningCategory\("(W\d+)"""".r
      .findAllMatchIn(read("src/main/scala/onion/compiler/Warning.scala"))
      .map(_.group(1))
      .toSet

  private def check(path: String): Unit = {
    val doc = read(path)
    assert(declared.nonEmpty, "no WarningCategory cases found — the scan has rotted")
    val missing = declared.filterNot(doc.contains).toSeq.sorted
    assert(missing.isEmpty, s"$path does not mention: ${missing.mkString(", ")}")

    // The reverse: a documented code that no longer exists sends readers looking for
    // a warning they can never see.
    val documented = """`(W\d{4})`""".r.findAllMatchIn(doc).map(_.group(1)).toSet
    val stale = (documented -- declared).toSeq.sorted
    assert(stale.isEmpty, s"$path documents codes that no longer exist: ${stale.mkString(", ")}")
  }

  it("the English compiler tool reference mentions every declared warning code, and no retired one") {
    check("docs/tools/compiler.md")
  }

  it("the Japanese compiler tool reference mentions every declared warning code, and no retired one") {
    check("docs/ja/tools/compiler.md")
  }
}
