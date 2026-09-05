package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Maps` module docs.
 *
 * `onion.Maps` is a genuine, default-imported stdlib module (`Maps::newMap`,
 * `Maps::getOrDefault`, `Maps::groupBy`, ...) with several members already guarded
 * individually by `MapsForEachShadowingSpec`/`MapsGroupByShadowingSpec`/
 * `MapsExtensionCallShadowingSpec`, but -- unlike `OnionMath`, `Stats`, `Net`, `Proc`,
 * `Scalars` and `DateTime`, each guarded by its own `*DocCoverageSpec` -- it has never had
 * a regression test checking that every member stays documented in both
 * docs/reference/stdlib.md and docs/ja/reference/stdlib.md. Every public member is checked
 * here so a future addition to onion.Maps fails the build instead of silently staying
 * undocumented.
 */
class MapsDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Maps::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Maps]
    val methodNames = c.getMethods
      .filter(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .filter(_.getDeclaringClass == c)
      .map(_.getName)
    methodNames.toSet
  }

  it("actual onion.Maps exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Maps found no static members — the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Maps member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Maps:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Maps member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Maps:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Maps in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Maps"),
      "docs/reference/stdlib.md's overview table should mention Maps")
    assert(read("docs/ja/reference/stdlib.md").contains("Maps"),
      "docs/ja/reference/stdlib.md's overview table should mention Maps")
  }
}
