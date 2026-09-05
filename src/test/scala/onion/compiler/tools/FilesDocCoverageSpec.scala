package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Files` module docs.
 *
 * `onion.Files` is a genuine, default-imported stdlib module (`Files::readText`,
 * `Files::writeLines`, `Files::glob`, ...) with 26 distinct public static member names, but --
 * unlike `OnionMath`, `Stats`, `Net`, `Proc`, `Scalars` and `DateTime`, each guarded by its own
 * `*DocCoverageSpec` -- it has never had a regression test checking that every member is
 * still documented in both docs/reference/stdlib.md and docs/ja/reference/stdlib.md. `Files`
 * is not in `ExtensionMethodFallbackSupport.BuiltinExtensionContainers`, so unlike
 * `Colls`/`Strings`/`Sets`/etc. it is called only ever as `Files::name(...)`, never as an
 * extension method -- the simple `Files::name` static-call spelling this guard looks for is
 * the only spelling that exists. Every public member is checked here so a future addition to
 * onion.Files fails the build instead of silently staying undocumented.
 */
class FilesDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Files::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Files]
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

  it("actual onion.Files exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Files found no static members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Files member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Files:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Files member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Files:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Files in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Files"),
      "docs/reference/stdlib.md's overview table should mention Files")
    assert(read("docs/ja/reference/stdlib.md").contains("Files"),
      "docs/ja/reference/stdlib.md's overview table should mention Files")
  }
}
