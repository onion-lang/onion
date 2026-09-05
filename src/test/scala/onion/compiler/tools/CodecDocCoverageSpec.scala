package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Codec` module docs.
 *
 * `onion.Codec` is a genuine, default-imported stdlib module (`Codec::base64Encode`,
 * `Codec::hexEncode`, `Codec::urlEncode`, ...) with 6 distinct public static member names, but --
 * unlike `OnionMath`, `Stats`, `Net`, `Proc`, `Scalars`, `DateTime`, `Files`, `Rand`, `Csv` and
 * `Hash`, each already guarded by its own `*DocCoverageSpec` -- it never had one. All 6 members
 * were already documented in both files; this guard now fails the build if a future addition to
 * onion.Codec goes undocumented.
 */
class CodecDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Codec::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Codec]
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

  it("actual onion.Codec exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Codec found no static members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Codec member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Codec:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Codec member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Codec:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Codec in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Codec"),
      "docs/reference/stdlib.md's overview table should mention Codec")
    assert(read("docs/ja/reference/stdlib.md").contains("Codec"),
      "docs/ja/reference/stdlib.md's overview table should mention Codec")
  }
}
