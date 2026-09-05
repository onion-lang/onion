package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Yaml` module docs.
 *
 * `onion.Yaml` is a genuine, default-imported stdlib module (`Yaml::parse`,
 * `Yaml::stringify`) with 2 distinct public static member names, but -- unlike
 * `OnionMath`, `Stats`, `Net`, `Proc`, `Scalars`, `DateTime`, `Files`, `Rand`, `Csv`, `Hash`,
 * `Codec`, `Text`, `Format` and `Assert`, each already guarded by its own coverage spec --
 * it never had one. Both members were already documented in both files; this guard now
 * fails the build if a future addition to `onion.Yaml` goes undocumented.
 */
class YamlDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Yaml::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Yaml]
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

  it("actual onion.Yaml exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Yaml found no static members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Yaml member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Yaml:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Yaml member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Yaml:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Yaml in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Yaml"),
      "docs/reference/stdlib.md's overview table should mention Yaml")
    assert(read("docs/ja/reference/stdlib.md").contains("Yaml"),
      "docs/ja/reference/stdlib.md's overview table should mention Yaml")
  }
}
