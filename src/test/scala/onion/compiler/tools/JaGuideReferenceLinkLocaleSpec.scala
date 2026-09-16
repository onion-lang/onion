package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/**
 * Drift guard: every cross-reference from a Markdown file under docs/ja/guide into the
 * reference docs uses the single-level `../reference/...` path, which resolves to the
 * maintained Japanese translations under docs/ja/reference/. A `../../reference/...`
 * path instead escapes back out to the English docs/reference/ pages, silently dropping
 * a Japanese reader out of their own locale. (Markdown files under docs/ja/contributing
 * intentionally link to the canonical English spec via `../../reference/...` and are out
 * of scope here.)
 */
class JaGuideReferenceLinkLocaleSpec extends AnyFunSpec {

  private val guideDir = Path.of("docs/ja/guide")

  private def guideFiles: List[Path] =
    Files.list(guideDir).iterator().asScala.filter(_.toString.endsWith(".md")).toList

  it("never links out to the English reference docs via ../../reference/") {
    val offenders = for {
      file <- guideFiles
      line <- Files.readString(file).linesIterator.zipWithIndex
      if line._1.contains("../../reference/")
    } yield s"${file}:${line._2 + 1}: ${line._1.trim}"

    assert(offenders.isEmpty,
      "docs/ja/guide/*.md must link to the Japanese reference docs via ../reference/..., " +
      s"not escape locale via ../../reference/...:\n${offenders.mkString("\n")}")
  }
}
