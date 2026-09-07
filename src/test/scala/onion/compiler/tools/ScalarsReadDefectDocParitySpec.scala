package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * `Scalars::read`'s defect never quotes the malformed text it found (unlike
 * `Scalars::coerce`, which quotes a `String` value it rejects) -- `Defect.at(origin,
 * path, tag, text)` in the `catch` branch of `Scalars.read` passes `text` straight
 * through. `docs/reference/stdlib.md` and its Japanese translation claimed the opposite
 * (`found "http"`, with quotes) even though the neighboring `docs/guide/shapes.md`
 * example, exercising the same code path, already showed it unquoted (`found abc`).
 * Pinned here -- against the actual runtime output, not just the doc text -- so the two
 * docs can't drift back to the wrong claim independently of each other or of the code.
 */
class ScalarsReadDefectDocParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private val actualDescribe: String = {
    val bad = onion.Scalars.read("Int", "http", null, "port")
    bad.defects().get(0).describe()
  }

  it("Scalars::read's defect does not quote the found text (sanity check)") {
    assert(actualDescribe == "port: expected Int, found http", actualDescribe)
  }

  it("docs/reference/stdlib.md's Scalars::read example matches the actual defect text") {
    assert(read("docs/reference/stdlib.md").contains("// " + actualDescribe))
  }

  it("docs/ja/reference/stdlib.md's Scalars::read example matches the actual defect text") {
    assert(read("docs/ja/reference/stdlib.md").contains("// " + actualDescribe))
  }
}
