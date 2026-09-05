package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Rand` module docs.
 *
 * `onion.Rand` is a default-imported stdlib module (`Rand::nextInt`, `Rand::shuffle`,
 * `Rand::uuid`, ...) with its own section in docs/reference/stdlib.md and
 * docs/ja/reference/stdlib.md, but -- unlike `DateTime`/`Stats`/`OnionMath`, the other
 * default-imported "date & random"/numeric modules, each already guarded by its own
 * `*DocCoverageSpec` -- it never had one. All 8 members were already documented in
 * both files; this guard now fails the build if a future addition to onion.Rand
 * goes undocumented.
 */
class RandDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Rand::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Rand]
    val methodNames = c.getMethods
      .filter(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .filter(_.getDeclaringClass == c)
      .map(_.getName)
    methodNames.toSet
  }

  it("actual onion.Rand exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Rand found no static members — the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Rand member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Rand:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Rand member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Rand:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Rand in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Rand"),
      "docs/reference/stdlib.md's overview table should mention Rand")
    assert(read("docs/ja/reference/stdlib.md").contains("Rand"),
      "docs/ja/reference/stdlib.md's overview table should mention Rand")
  }
}
