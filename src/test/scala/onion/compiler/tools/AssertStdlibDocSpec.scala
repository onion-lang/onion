package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the `Assert` module docs (issue #449).
 *
 * `docs/reference/stdlib.md`, its Japanese copy, and `CLAUDE.md` documented
 * `assertTrue`/`assertFalse`/`assertEquals`/`assertNotEquals`/`assertNotNull`/`assertNull`,
 * none of which exist on `onion.Assert` (the real names are `isTrue`, `isFalse`, `equals`,
 * `notEquals`, `notNull`, `isNull`). Every documented `Assert::name` is checked here
 * against the actual public static method names via reflection, so a future rename on
 * either side fails the build instead of silently rotting again.
 */
class AssertStdlibDocSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Assert::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] =
    classOf[onion.Assert].getMethods
      .filter(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .filter(_.getDeclaringClass == classOf[onion.Assert])
      .map(_.getName)
      .toSet

  it("actual onion.Assert exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Assert found no static methods — the scan has rotted")
  }

  it("docs/reference/stdlib.md documents only Assert methods that actually exist") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = documented -- actualNames
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md documents Assert:: names not on onion.Assert: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents only Assert methods that actually exist") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = documented -- actualNames
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md documents Assert:: names not on onion.Assert: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("CLAUDE.md's Assert bullet names only methods that actually exist") {
    val claudeMd = read("CLAUDE.md")
    val bulletLine = claudeMd.linesIterator.find(_.contains("`Assert`")).getOrElse(
      fail("CLAUDE.md no longer has an `Assert` bullet — update this guard"))
    val named = """\b([a-zA-Z]+)\b""".r.findAllMatchIn(
      bulletLine.substring(bulletLine.indexOf('('))).map(_.group(1)).toSet
    val missing = named -- actualNames
    assert(missing.isEmpty,
      s"CLAUDE.md's Assert bullet names methods not on onion.Assert: ${missing.toSeq.sorted.mkString(", ")} (line: $bulletLine)")
  }
}
