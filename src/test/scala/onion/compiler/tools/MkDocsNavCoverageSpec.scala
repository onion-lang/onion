package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/**
 * Drift guard for mkdocs.yml's nav (issue #486): `mkdocs build --strict` warns about
 * every file under `docs/` that isn't reachable from `nav`, and nothing before this
 * tied the two together — `docs/superpowers` (agent-facing planning material, never
 * meant to be part of the public site) already drifted out of step this way once.
 * This walks `docs/` and asserts every markdown file is either referenced from `nav`
 * or named under `exclude_docs`, so a newly orphaned file fails the build instead of
 * just adding another silent warning line.
 */
class MkDocsNavCoverageSpec extends AnyFunSpec {

  private val docsRoot = Path.of("docs")
  private val mkdocsYml = Files.readString(Path.of("mkdocs.yml"))

  /** Every `foo/bar.md`-shaped token written anywhere in mkdocs.yml (the nav values). */
  private lazy val referenced: Set[String] =
    """[\w][\w./-]*\.md""".r.findAllMatchIn(mkdocsYml).map(_.matched).toSet

  /** `exclude_docs:` is a literal-block scalar of one gitignore-style pattern per line. */
  private lazy val excluded: Set[String] =
    """(?m)^exclude_docs:\s*\|\s*\n((?:^[ \t]+\S.*\n?)*)""".r
      .findFirstMatchIn(mkdocsYml)
      .map(_.group(1).linesIterator.map(_.trim).filter(_.nonEmpty).toSet)
      .getOrElse(Set.empty)

  private def isExcluded(relPath: String): Boolean =
    excluded.exists(pat => if (pat.endsWith("/")) relPath.startsWith(pat) else relPath == pat)

  private lazy val allMarkdownFiles: Seq[String] = {
    val stream = Files.walk(docsRoot)
    try
      stream.iterator().asScala
        .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".md"))
        .map(p => docsRoot.relativize(p).toString.replace('\\', '/'))
        .toSeq
    finally stream.close()
  }

  it("every docs/**/*.md file is reachable from mkdocs.yml's nav or listed in exclude_docs") {
    assert(allMarkdownFiles.nonEmpty, "no markdown files found under docs/ — the scan has rotted")
    val orphans = allMarkdownFiles.filterNot(isExcluded).filterNot(referenced.contains).sorted
    assert(orphans.isEmpty,
      s"not in nav and not excluded (mkdocs --strict warns on these): ${orphans.mkString(", ")}")
  }
}
