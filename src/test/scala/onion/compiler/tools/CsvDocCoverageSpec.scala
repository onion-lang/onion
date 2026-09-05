package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Csv` module docs.
 *
 * `onion.Csv` is a genuine, default-imported stdlib module (`Csv::parse`, `Csv::stringify`,
 * `Csv::column`, ...) with 6 distinct public static member names, but -- unlike `OnionMath`,
 * `Stats`, `Net`, `Proc`, `Scalars`, `DateTime`, `Files` and `Rand`, each guarded by its own
 * `*DocCoverageSpec`/`*ParitySpec` -- it has never had a regression test checking that every
 * member is still documented in both docs/reference/stdlib.md and docs/ja/reference/stdlib.md.
 * `Csv` is not in `ExtensionMethodFallbackSupport.BuiltinExtensionContainers`, so unlike
 * `Colls`/`Strings`/`Sets`/etc. it is called only ever as `Csv::name(...)`, never as an
 * extension method -- the simple `Csv::name` static-call spelling this guard looks for is the
 * only spelling that exists. Every public member is checked here so a future addition to
 * onion.Csv fails the build instead of silently staying undocumented.
 */
class CsvDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Csv::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Csv]
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

  it("actual onion.Csv exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Csv found no static members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Csv member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Csv:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Csv member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Csv:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Csv in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Csv"),
      "docs/reference/stdlib.md's overview table should mention Csv")
    assert(read("docs/ja/reference/stdlib.md").contains("Csv"),
      "docs/ja/reference/stdlib.md's overview table should mention Csv")
  }
}
