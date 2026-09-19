package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for `docs/reference/effects.md` / `docs/ja/reference/effects.md`.
 *
 * Both files once described the capability layer built on `--effects` (`tool`
 * declarations, `requires`, `--plan`, documented in `docs/guide/tools.md`) as
 * forthcoming ("is described in the guide once it lands" / "はガイド側で解説します"),
 * even though `docs/guide/tools.md` shipped in the very same commit and the
 * capability checks it describes (E0077-E0080, `CapabilityCheckPass`) are fully
 * implemented and tested. A reader had no way to tell the layer already existed.
 * Both copies must point at the guide instead of describing it as unlanded.
 */
class EffectsDocCapabilityLayerLandedSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private val docs = Seq(
    "docs/reference/effects.md",
    "docs/ja/reference/effects.md"
  )

  it("does not describe the capability layer as unlanded") {
    for (doc <- docs) {
      val text = read(doc)
      assert(!text.contains("once it lands"),
        s"$doc still describes the capability layer as forthcoming, even though " +
        "docs/guide/tools.md already documents it")
      assert(!text.contains("はガイド側で解説します"),
        s"$doc still uses the stale future-tense phrasing for the capability layer")
    }
  }

  it("links to the tools guide that documents the capability layer") {
    for (doc <- docs) {
      val text = read(doc)
      assert(text.contains("guide/tools.md"),
        s"$doc does not point a reader at docs/guide/tools.md, which documents the " +
        "capability layer (tool declarations, requires, --plan) built on this page's data")
    }
  }
}
