package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for docs/tools/language-server.md's formatting section.
 *
 * The LSP formatting provider shares `OnionFormatter` with `onion fmt`. That formatter's own
 * doc comment and `OnionFormatterSpec` establish that leading tabs are preserved, never
 * rewritten to spaces -- an earlier version rebuilt indentation from JavaCC's tab-expanding
 * column numbers and silently turned tab-indented files into eight-space-indented ones (110
 * lines of churn in one sample file); the current formatter slices whitespace out of the
 * original text instead, specifically to avoid that bug. docs/tools/language-server.md (and
 * its Japanese translation) nonetheless claimed the opposite: "converts leading tabs to
 * spaces" -- contradicted by the sibling doc page docs/tools/project-cli.md, which correctly
 * says of the same formatter "tabs included".
 */
class LanguageServerFormattingDocSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  it("actual formatter preserves a leading tab (sanity check)") {
    val source = "import {\n\tjava.util.*\n}\n"
    assert(
      onion.tools.format.OnionFormatter.format(source).text == source,
      "onion.tools.format.OnionFormatter unexpectedly rewrote a leading tab -- the scan has rotted"
    )
  }

  it("docs/tools/language-server.md does not claim the formatter converts leading tabs to spaces") {
    val doc = read("docs/tools/language-server.md")
    assert(
      !doc.contains("converts leading tabs to spaces"),
      "docs/tools/language-server.md claims the formatter converts leading tabs to spaces, " +
        "but OnionFormatter preserves tabs (see OnionFormatterSpec's \"keeps tabs\" cases)"
    )
  }

  it("docs/ja/tools/language-server.md does not claim the formatter converts leading tabs to spaces") {
    val doc = read("docs/ja/tools/language-server.md")
    assert(
      !doc.contains("行頭のタブをスペースに変換"),
      "docs/ja/tools/language-server.md claims the formatter converts leading tabs to spaces, " +
        "but OnionFormatter preserves tabs"
    )
  }
}
