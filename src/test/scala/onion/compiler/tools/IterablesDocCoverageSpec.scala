package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Iterables` module docs.
 *
 * `onion.Iterables` is a default-imported stdlib module (`Iterables::map`,
 * `Iterables::filter`, `Iterables::foldl`, ...) with 16 distinct public static
 * member names, but -- unlike `Strings`/`Regex`/`Csv`, the other default-imported
 * modules, each already guarded by its own `*DocCoverageSpec` -- it never had one.
 * All 16 members were already documented in both files; this guard now fails the
 * build if a future addition to `onion.Iterables` goes undocumented.
 */
class IterablesDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Iterables::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Iterables]
    val methodNames = c.getMethods
      .filter(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .filter(_.getDeclaringClass == c)
      .map(_.getName)
    methodNames.toSet
  }

  it("actual onion.Iterables exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Iterables found no static members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Iterables member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Iterables:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Iterables member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Iterables:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Iterables in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Iterables"),
      "docs/reference/stdlib.md's overview table should mention Iterables")
    assert(read("docs/ja/reference/stdlib.md").contains("Iterables"),
      "docs/ja/reference/stdlib.md's overview table should mention Iterables")
  }
}
