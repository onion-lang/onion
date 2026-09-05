package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Sets` module docs.
 *
 * `onion.Sets` is a genuine, default-imported stdlib module (`Sets::newSet`,
 * `Sets::union`, `Sets::isDisjoint`, ...) with several members already guarded
 * individually by `SetsForEachShadowingSpec`/`SetsMapExtensionCallShadowingSpec`/
 * `SetsToListExtensionCallShadowingSpec`/`SetsUnionIntersectionDifferenceShadowingSpec`/
 * `SetsContainsAllExtensionCallShadowingSpec`/`SetsExtensionCallDocSpec`, but -- unlike
 * `Maps`, guarded by `MapsDocCoverageSpec` -- it has never had a regression test checking
 * that every member stays documented in both docs/reference/stdlib.md and
 * docs/ja/reference/stdlib.md. Every public member is checked here so a future addition to
 * onion.Sets fails the build instead of silently staying undocumented.
 */
class SetsDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Sets::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Sets]
    val methodNames = c.getMethods
      .filter(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .filter(_.getDeclaringClass == c)
      .map(_.getName)
    methodNames.toSet
  }

  it("actual onion.Sets exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Sets found no static members — the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Sets member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Sets:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Sets member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Sets:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Sets in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Sets"),
      "docs/reference/stdlib.md's overview table should mention Sets")
    assert(read("docs/ja/reference/stdlib.md").contains("Sets"),
      "docs/ja/reference/stdlib.md's overview table should mention Sets")
  }
}
