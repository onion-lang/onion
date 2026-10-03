package onion.compiler.tools

import java.nio.file.{Files, Path}

import org.scalatest.funspec.AnyFunSpec

import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 * Drift guard for Maven Central publishing.
 *
 * The groupId is `org.onion-lang` (verified on the Central Portal through DNS on
 * onion-lang.org); it used to be `org.onion_lang`, which was never published. A
 * `//> using dep` example or an `onion.toml` snippet still naming the old group would
 * resolve to nothing, so no doc, battery source or build file may write it as a
 * coordinate. (`org.onion_lang` as an Onion *module* name, e.g. in the compiler docs'
 * output-directory example, is a package name, not a coordinate, and stays legal.)
 *
 * The publishing procedure lives in three copies of the release checklist; all of them
 * must name the secrets the `publish-central` job reads, or the one-time setup cannot be
 * followed from the copy a reader happens to open.
 */
class MavenCentralPublishingDocSpec extends AnyFunSpec {

  private def read(p: Path): String = Files.readString(p)

  private def files(root: String, ext: String*): Seq[Path] =
    Using.resource(Files.walk(Path.of(root))) { s =>
      s.iterator().asScala
        .filter(Files.isRegularFile(_))
        .filter(p => ext.exists(e => p.toString.endsWith(e)))
        .toList
    }

  it("declares the org.onion-lang groupId in build.sbt") {
    val build = read(Path.of("build.sbt"))
    assert(build.contains("organization := \"org.onion-lang\""))
    assert(!build.contains("org.onion_lang"), "build.sbt still names the old org.onion_lang group")
  }

  it("never writes the old org.onion_lang group as a Maven coordinate") {
    val candidates =
      files("docs", ".md") ++ files("batteries", ".java", ".scala", ".md") :+ Path.of("README.md")
    val offenders = candidates.filter(p => read(p).contains("org.onion_lang:"))
    assert(offenders.isEmpty,
      s"these still use the unpublished org.onion_lang groupId: ${offenders.mkString(", ")}")
  }

  it("documents the Central Portal secrets and upload in every release checklist") {
    val docs = Seq("RELEASING.md", "docs/RELEASING.md", "docs/ja/RELEASING.md")
    val required = Seq("CENTRAL_USERNAME", "CENTRAL_PASSWORD", "PGP_SECRET", "PGP_PASSPHRASE",
      "org.onion-lang", "sonaUpload", "CENTRAL_AUTO_RELEASE")
    for (doc <- docs; term <- required)
      assert(read(Path.of(doc)).contains(term), s"$doc does not mention $term")
  }

  it("reads every secret it documents in the release workflow") {
    val workflow = read(Path.of(".github/workflows/release.yml"))
    for (name <- Seq("CENTRAL_USERNAME", "CENTRAL_PASSWORD", "PGP_SECRET", "PGP_PASSPHRASE"))
      assert(workflow.contains(s"secrets.$name"), s"release.yml does not read secrets.$name")
    assert(workflow.contains("centralStage"))
  }
}
