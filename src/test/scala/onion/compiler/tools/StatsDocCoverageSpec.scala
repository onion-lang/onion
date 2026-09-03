package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Stats` module docs.
 *
 * `onion.Stats` is a default-imported stdlib module (`Stats::sum`, `Stats::sumLong`,
 * `Stats::median`, ...) with its own section in docs/reference/stdlib.md and
 * docs/ja/reference/stdlib.md, but `sumLong` was only ever mentioned in prose
 * ("`sumInt`/`sumLong` keep integer precision") and never shown as an actual
 * `Stats::sumLong(...)` call — every public member is checked here against both
 * files so a future addition to onion.Stats fails the build instead of silently
 * staying undocumented.
 */
class StatsDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Stats::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Stats]
    val methodNames = c.getMethods
      .filter(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .filter(_.getDeclaringClass == c)
      .map(_.getName)
    methodNames.toSet
  }

  it("actual onion.Stats exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Stats found no static members — the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Stats member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Stats:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Stats member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Stats:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Stats in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Stats"),
      "docs/reference/stdlib.md's overview table should mention Stats")
    assert(read("docs/ja/reference/stdlib.md").contains("Stats"),
      "docs/ja/reference/stdlib.md's overview table should mention Stats")
  }
}
