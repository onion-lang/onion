package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Codec` module docs.
 *
 * `onion.Codec` is a default-imported stdlib module (`Codec::base64Encode`,
 * `Codec::hexEncode`, `Codec::urlEncode`, ...) with its own section in
 * docs/reference/stdlib.md and docs/ja/reference/stdlib.md, but -- unlike `OnionMath`,
 * `Stats`, `Net`, `Proc`, `Scalars`, `DateTime` and `Files`, each already guarded by its
 * own `*DocCoverageSpec` -- it never had a regression test checking that every member is
 * still documented in both files. `Codec` is also registered in
 * `ExtensionMethodFallbackSupport.BuiltinExtensionContainers`, so its methods are callable
 * both as `Codec::base64Encode(s)` and as the extension call `s.base64Encode()`; both docs
 * already show the `Codec::name` spelling for every member, so this guard checks that
 * spelling directly, the same way `StatsDocCoverageSpec` does for `onion.Stats` (also a
 * builtin extension container). Every public member is checked here so a future addition to
 * onion.Codec fails the build instead of silently staying undocumented.
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
    methodNames.toSet
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
