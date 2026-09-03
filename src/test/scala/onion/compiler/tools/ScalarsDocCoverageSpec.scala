package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Scalars` module docs.
 *
 * `onion.Scalars` (`toBoolean`/`isBoolean`/`read`/`coerce`) was listed in the "Modules at
 * a glance" table in both docs/reference/stdlib.md and docs/ja/reference/stdlib.md but had
 * no dedicated section documenting any of its members in either file.
 */
class ScalarsDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String, names: Set[String]): Set[String] =
    names.filter(n => s"\\b${java.util.regex.Pattern.quote(n)}\\b".r.findFirstIn(doc).isDefined)

  private def staticNames(c: Class[?]): Set[String] =
    c.getDeclaredMethods
      .filter(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .filter(m => java.lang.reflect.Modifier.isPublic(m.getModifiers))
      .map(_.getName)
      .toSet

  private lazy val actualNames: Set[String] = staticNames(classOf[onion.Scalars])

  it("actual onion.Scalars exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Scalars found no members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Scalars member") {
    val missing = actualNames -- documentedNames(read("docs/reference/stdlib.md"), actualNames)
    assert(missing.isEmpty, s"docs/reference/stdlib.md is missing Scalars members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Scalars member") {
    val missing = actualNames -- documentedNames(read("docs/ja/reference/stdlib.md"), actualNames)
    assert(missing.isEmpty, s"docs/ja/reference/stdlib.md is missing Scalars members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/reference/stdlib.md's Modules at a glance table names Scalars") {
    assert(read("docs/reference/stdlib.md").contains("`Scalars`"))
  }

  it("docs/ja/reference/stdlib.md's Modules at a glance table names Scalars") {
    assert(read("docs/ja/reference/stdlib.md").contains("`Scalars`"))
  }
}
