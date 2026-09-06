package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Format` module docs.
 *
 * `onion.Format` is a genuine, default-imported stdlib module (`Format::integer`,
 * `Format::number`, `Format::fixed`, `Format::percent`, `Format::bytes`, `Format::duration`,
 * `Format::ordinal`) with 7 distinct public static member names, but -- unlike `OnionMath`,
 * `Stats`, `Net`, `Proc`, `Scalars`, `DateTime`, `Files`, `Rand`, `Csv`, `Hash` and `Codec`, each
 * guarded by its own `*DocCoverageSpec` -- it has never had a regression test checking that
 * every member is still documented in both docs/reference/stdlib.md and
 * docs/ja/reference/stdlib.md. Every public member is checked here so a future addition to
 * onion.Format fails the build instead of silently staying undocumented.
 */
class FormatDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Format::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Format]
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

  it("actual onion.Format exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Format found no static members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Format member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Format:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Format member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Format:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Format in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Format"),
      "docs/reference/stdlib.md's overview table should mention Format")
    assert(read("docs/ja/reference/stdlib.md").contains("Format"),
      "docs/ja/reference/stdlib.md's overview table should mention Format")
  }
}
