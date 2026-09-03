package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Regex` module docs.
 *
 * `onion.Regex` exposes only static factories. docs/reference/stdlib.md and
 * docs/ja/reference/stdlib.md document `matches`/`find`/`findAll`/`findFirst`/`groups`/
 * `groupsAll`/`replace`/`replaceFirst`/`split`/`quote`/`isValid`, but never mention
 * `matchGroups` -- the anchored, whole-string match used to desugar the `case re"..."
 * (a, b):` select pattern described in CLAUDE.md -- anywhere in either file.
 *
 * `Object` overrides (`toString`, `equals`, `hashCode`, ...) are excluded: they carry no
 * Regex-specific behavior worth documenting.
 */
class RegexDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String, names: Set[String]): Set[String] =
    names.filter(n => s"\\b${java.util.regex.Pattern.quote(n)}\\b".r.findFirstIn(doc).isDefined)

  private val objectOverrides = Set("toString", "equals", "hashCode", "wait", "notify", "notifyAll", "getClass")

  private def staticNames(c: Class[?]): Set[String] =
    c.getDeclaredMethods
      .filter(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .filter(m => java.lang.reflect.Modifier.isPublic(m.getModifiers))
      .map(_.getName)
      .filterNot(objectOverrides.contains)
      .toSet

  private lazy val actualNames: Set[String] = staticNames(classOf[onion.Regex])

  it("actual onion.Regex exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Regex found no members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Regex member") {
    val missing = actualNames -- documentedNames(read("docs/reference/stdlib.md"), actualNames)
    assert(missing.isEmpty, s"docs/reference/stdlib.md is missing Regex members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Regex member") {
    val missing = actualNames -- documentedNames(read("docs/ja/reference/stdlib.md"), actualNames)
    assert(missing.isEmpty, s"docs/ja/reference/stdlib.md is missing Regex members: ${missing.toSeq.sorted.mkString(", ")}")
  }
}
