package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Assert` module docs.
 *
 * `onion.Assert` is a genuine, default-imported stdlib module (`Assert::isTrue`,
 * `Assert::isFalse`, `Assert::equals`, `Assert::notEquals`, `Assert::notNull`, `Assert::isNull`,
 * `Assert::fail`) with 7 distinct public static member names, but -- unlike `OnionMath`, `Stats`,
 * `Net`, `Proc`, `Scalars`, `DateTime`, `Files`, `Rand`, `Csv`, `Hash` and `Codec`, each guarded
 * by its own `*DocCoverageSpec` -- it has never had a regression test checking that every member
 * is still documented in both docs/reference/stdlib.md and docs/ja/reference/stdlib.md. All 7
 * members were already documented in both files; this guard now fails the build if a future
 * addition to onion.Assert goes undocumented.
 */
class AssertDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Assert::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Assert]
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

  it("actual onion.Assert exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Assert found no static members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Assert member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Assert:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Assert member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Assert:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Assert in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Assert"),
      "docs/reference/stdlib.md's overview table should mention Assert")
    assert(read("docs/ja/reference/stdlib.md").contains("Assert"),
      "docs/ja/reference/stdlib.md's overview table should mention Assert")
  }
}
