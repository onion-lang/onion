package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Server` module docs.
 *
 * `onion.Server` is a genuine, default-imported stdlib module (`Server::start`,
 * `Server::text`, `Server::json`, `Server::html`, `Server::notFound`,
 * `Server::status`) with 6 distinct public static member names, but -- unlike
 * `OnionMath`, `Stats`, `Net`, `Proc`, `Scalars`, `DateTime`, `Files`, `Rand`,
 * `Csv`, `Hash`, `Codec`, `Text`, `Format`, `Assert`, `Yaml` and `Archive`,
 * each already guarded by its own coverage spec -- it never had one. All 6
 * members were already documented in both files; this guard now fails the
 * build if a future addition to `onion.Server` goes undocumented.
 */
class ServerDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Server::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Server]
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

  it("actual onion.Server exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Server found no static members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Server member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Server:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Server member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Server:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Server in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Server"),
      "docs/reference/stdlib.md's overview table should mention Server")
    assert(read("docs/ja/reference/stdlib.md").contains("Server"),
      "docs/ja/reference/stdlib.md's overview table should mention Server")
  }
}
