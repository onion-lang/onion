package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Hash` module docs.
 *
 * `onion.Hash` is a genuine, default-imported stdlib module (`Hash::md5`, `Hash::sha1`,
 * `Hash::sha256`, `Hash::sha512`) with 4 distinct public static member names, but -- unlike
 * `OnionMath`, `Stats`, `Net`, `Proc`, `Scalars`, `DateTime`, `Files`, `Rand` and `Csv`, each
 * guarded by its own `*DocCoverageSpec` -- it has never had a regression test checking that
 * every member is still documented in both docs/reference/stdlib.md and
 * docs/ja/reference/stdlib.md. `Hash` is also registered as a builtin extension container
 * (`"pw".sha256()` works, guarded separately by `HashCodecTextFormatExtensionCallDocSpec`), but
 * that spec only checks the extension-call spelling in the Hash Module section -- it does not
 * check that every member of `onion.Hash` is documented at all. Every public member is checked
 * here so a future addition to onion.Hash fails the build instead of silently staying
 * undocumented.
 */
class HashDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Hash::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Hash]
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

  it("actual onion.Hash exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Hash found no static members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Hash member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Hash:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Hash member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Hash:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Hash in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Hash"),
      "docs/reference/stdlib.md's overview table should mention Hash")
    assert(read("docs/ja/reference/stdlib.md").contains("Hash"),
      "docs/ja/reference/stdlib.md's overview table should mention Hash")
  }
}
