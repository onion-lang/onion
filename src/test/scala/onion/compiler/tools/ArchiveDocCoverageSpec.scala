package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Archive` module docs.
 *
 * `onion.Archive` is a genuine, default-imported stdlib module (`Archive::zip`,
 * `Archive::zipDir`, `Archive::entries`, `Archive::unzip`, `Archive::gzip`,
 * `Archive::gunzip`, `Archive::gzipFile`, `Archive::gunzipFile`) with 8 distinct
 * public static member names, but -- unlike `OnionMath`, `Stats`, `Net`, `Proc`,
 * `Scalars`, `DateTime`, `Files`, `Rand`, `Csv`, `Hash`, `Codec`, `Text`, `Format`,
 * `Assert` and `Yaml`, each already guarded by its own coverage spec -- it never
 * had one. All 8 members were already documented in both files; this guard now
 * fails the build if a future addition to `onion.Archive` goes undocumented.
 */
class ArchiveDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Archive::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Archive]
    val methodNames = c.getMethods
      .filter(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .filter(_.getDeclaringClass == c)
      .map(_.getName)
    val fieldNames = c.getFields
      .filter(f => java.lang.reflect.Modifier.isStatic(f.getModifiers))
      .filter(_.getDeclaringClass == c)
      .map(_.getName)
    (methodNames ++ fieldNames).toSet
  }

  it("actual onion.Archive exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Archive found no static members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Archive member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Archive:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Archive member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Archive:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Archive in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Archive"),
      "docs/reference/stdlib.md's overview table should mention Archive")
    assert(read("docs/ja/reference/stdlib.md").contains("Archive"),
      "docs/ja/reference/stdlib.md's overview table should mention Archive")
  }
}
