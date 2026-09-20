package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * docs/ja/reference/stdlib.md's "## Resources Module" section heading was left
 * untranslated (copy-pasted verbatim from docs/reference/stdlib.md), even
 * though its own body text is fully in Japanese and every sibling module
 * heading in the file follows the "## <Name> モジュール" convention (e.g.
 * "## Json モジュール", "## Csv モジュール", "## Files モジュール"). A
 * Japanese-only reader hit a stray English heading in an otherwise-translated
 * reference page. Guards that the heading reads "## Resources モジュール"
 * like its siblings.
 */
class StdlibDocResourcesModuleHeadingLocaleSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  it("translates the Resources Module heading like every other module heading in the file") {
    val ja = read("docs/ja/reference/stdlib.md")
    assert(!ja.linesIterator.exists(_.trim == "## Resources Module"),
      "docs/ja/reference/stdlib.md still has the untranslated English heading \"## Resources Module\" " +
      "instead of \"## Resources モジュール\"")
    assert(ja.linesIterator.exists(_.trim == "## Resources モジュール"),
      "docs/ja/reference/stdlib.md is missing the translated \"## Resources モジュール\" heading")
  }
}
