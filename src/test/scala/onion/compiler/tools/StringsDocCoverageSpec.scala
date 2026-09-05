package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Strings` module docs.
 *
 * `onion.Strings` is a large, default-imported stdlib module (`Strings::trim`,
 * `Strings::padLeft`, `Strings::capitalizeWords`, ...) with almost 40 distinct public static
 * member names, several already guarded individually against native-method shadowing by
 * `StringsExtensionCallShadowingSpec`/`StringsIsBlankExtensionCallShadowingSpec`/
 * `StringsJoinExtensionCallShadowingSpec`/`StringsReplaceExtensionCallShadowingSpec`/
 * `StringsEndsWithExtensionCallShadowingSpec`/`StringsContainsIsEmptyExtensionCallShadowingSpec`/
 * `StringsTrimStartsWithIndexOfExtensionCallShadowingSpec` -- but, unlike `Maps`/`Sets`/`Csv`,
 * it has never had a regression test checking that every member stays documented in both
 * docs/reference/stdlib.md and docs/ja/reference/stdlib.md using the `Strings::name` spelling.
 * Every public member is checked here so a future addition to onion.Strings fails the build
 * instead of silently staying undocumented.
 */
class StringsDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def documentedNames(doc: String): Set[String] =
    """Strings::(\w+)""".r.findAllMatchIn(doc).map(_.group(1)).toSet

  private lazy val actualNames: Set[String] = {
    val c = classOf[onion.Strings]
    val methodNames = c.getMethods
      .filter(m => java.lang.reflect.Modifier.isStatic(m.getModifiers))
      .filter(_.getDeclaringClass == c)
      .map(_.getName)
    methodNames.toSet
  }

  it("actual onion.Strings exposes the names this guard assumes (sanity check)") {
    assert(actualNames.nonEmpty, "reflection on onion.Strings found no static members -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents every onion.Strings member") {
    val documented = documentedNames(read("docs/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/reference/stdlib.md is missing Strings:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("docs/ja/reference/stdlib.md documents every onion.Strings member") {
    val documented = documentedNames(read("docs/ja/reference/stdlib.md"))
    val missing = actualNames -- documented
    assert(missing.isEmpty,
      s"docs/ja/reference/stdlib.md is missing Strings:: members: ${missing.toSeq.sorted.mkString(", ")}")
  }

  it("\"Modules at a glance\" mentions Strings in both languages") {
    assert(read("docs/reference/stdlib.md").contains("Strings"),
      "docs/reference/stdlib.md's overview table should mention Strings")
    assert(read("docs/ja/reference/stdlib.md").contains("Strings"),
      "docs/ja/reference/stdlib.md's overview table should mention Strings")
  }
}
