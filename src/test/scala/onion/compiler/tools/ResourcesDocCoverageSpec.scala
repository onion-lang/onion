package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Resources` module docs.
 *
 * `onion.Resources` is on the default static import list (`default-static-imports.txt`),
 * alongside `Strings`, `Iterables`, `Regex` and `Csv` -- so its three factory functions
 * (`file`, `http`, `re`) resolve unqualified, exactly like the scheme-prefixed literals
 * (`file"…"`, `http"…"`, `re"…"`) they back. Every other default-imported class has its own
 * `## ClassName Module` section in `docs/reference/stdlib.md`, but `Resources` has never had
 * one, and it is missing from the "Modules at a glance" table too -- so a reader relying on
 * either the table or the module list never learns the bare `file(...)`/`http(...)`/`re(...)`
 * calls exist independently of the literal sugar.
 */
class ResourcesDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Resources::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Resources]
    val methodNames = c.getMethods
      .filter(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .filter(_.getDeclaringClass == c)
      .map(_.getName)
    methodNames.toSet
  }

  it("actual onion.Resources exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Resources found no static members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Resources member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Resources:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Resources member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Resources:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Resources in both languages") {
    val enTable = read("docs/reference/stdlib.md").linesIterator.toSeq
    val enStart = enTable.indexWhere(_.contains("## Modules at a glance"))
    assert(enStart >= 0, "docs/reference/stdlib.md has no 'Modules at a glance' section -- the scan has rotted")
    val en = enTable.drop(enStart + 1).takeWhile(!_.trim.startsWith("Most helpers")).mkString("\n")
    assert(en.contains("`Resources`"), "docs/reference/stdlib.md's glance table should mention `Resources`")

    val jaTable = read("docs/ja/reference/stdlib.md").linesIterator.toSeq
    val jaStart = jaTable.indexWhere(_.contains("## モジュール一覧"))
    assert(jaStart >= 0, "docs/ja/reference/stdlib.md has no 'モジュール一覧' section -- the scan has rotted")
    val ja = jaTable.drop(jaStart + 1).takeWhile(!_.trim.startsWith("ほとんど")).mkString("\n")
    assert(ja.contains("`Resources`"), "docs/ja/reference/stdlib.md's glance table should mention `Resources`")
  }

  it("CLAUDE.md's Standard Library list mentions Resources") {
    assert(read("CLAUDE.md").contains("`Resources`"), "CLAUDE.md's Standard Library list should mention `Resources`")
  }

  it("CLAUDE_ja.md's 標準ライブラリ list mentions Resources") {
    assert(read("docs/ja/CLAUDE_ja.md").contains("`Resources`"), "CLAUDE_ja.md's stdlib list should mention `Resources`")
  }
}
