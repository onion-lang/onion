package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard across the three copies of the release checklist: `RELEASING.md` (repo root),
 * `docs/RELEASING.md` (English, published by mkdocs) and `docs/ja/RELEASING.md` (Japanese
 * translation of the latter).
 *
 * Pushing a `v*` tag from a client is rejected by the repository's tag-protection ruleset
 * with an HTTP 403 (issue #334, re-confirmed by #1184) — the only reliable path is
 * `workflow_dispatch` on `.github/workflows/release.yml`, which creates the tag itself with
 * the Actions token. The root file documents this; the docs/ pair was never updated when it
 * was added and still tells a reader to `git push origin vX.Y.Z` as the only option, with no
 * mention of the 403 or the workaround — the exact failure mode #1184 spent 12 attempts
 * rediscovering. All three must mention the `workflow_dispatch` fallback so this can't
 * silently drop out of one copy again.
 */
class ReleasingDocWorkflowDispatchParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private val docs = Seq(
    "RELEASING.md",
    "docs/RELEASING.md",
    "docs/ja/RELEASING.md"
  )

  it("documents the workflow_dispatch fallback for the tag-push 403 in every copy") {
    for (doc <- docs) {
      val text = read(doc)
      assert(text.contains("workflow_dispatch"),
        s"$doc does not mention workflow_dispatch — a reader following it would hit the " +
        "tag-push 403 with no documented way out")
      assert(text.contains("403"),
        s"$doc does not explain the tag-push HTTP 403, so a reader can't tell why " +
        "`git push origin vX.Y.Z` alone isn't reliable")
    }
  }

  it("documents the bilingual testFull verification step in every copy") {
    for (doc <- docs) {
      val text = read(doc)
      assert(text.contains("testFull"),
        s"$doc does not mention testFull — `test` delegates to `testQuick` under sbt 2 " +
        "and can silently report 'No tests to run'")
    }
  }

  it("documents the sbt 2 nested target layout for local artifacts in every copy") {
    for (doc <- docs) {
      val text = read(doc)
      assert(text.contains("target/out/jvm/scala-3.3.7/onion/"),
        s"$doc still points at the pre-sbt-2 flat target/ layout for local jar/zip output")
    }
  }
}
