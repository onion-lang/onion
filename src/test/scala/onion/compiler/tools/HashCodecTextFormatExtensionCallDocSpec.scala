package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * `onion.Hash`, `onion.Codec`, `onion.Text` and `onion.Format` are all registered as
 * builtin extension methods (`ExtensionMethodFallbackSupport.BuiltinExtensionContainers`
 * auto-registers every public static method of these containers as an extension of its
 * first parameter's type), so `"pw".sha256()`, `"Hi".base64Encode()`, `text.wrap(40)` and
 * `(1536L).bytes()` all compile and run (verified by `StdlibExtensionChainSpec`) -- but
 * docs/reference/stdlib.md and its Japanese translation only ever spelled these as
 * `Hash::name(...)` / `Codec::name(...)` / `Text::name(...)` / `Format::name(...)` static
 * calls, in all four Module sections, in both languages.
 */
class HashCodecTextFormatExtensionCallDocSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def section(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toIndexedSeq
    val start = lines.indexWhere(_.contains(heading))
    assert(start >= 0, s"""could not find a "$heading" heading -- the scan has rotted""")
    val rest = lines.drop(start + 1)
    val end = rest.indexWhere(_.startsWith("## "))
    (if (end < 0) rest else rest.take(end)).mkString("\n")
  }

  private val cases = Seq(
    ("## Hash Module", "## Hash モジュール", Set("\"password\".sha256(")),
    ("## Codec Module", "## Codec モジュール", Set("\"Hello\".base64Encode(")),
    ("## Text Module", "## Text モジュール", Set(".wrap(40)")),
    ("## Format Module", "## Format モジュール", Set("1536L).bytes("))
  )

  cases.foreach { case (enHeading, jaHeading, extensionCalls) =>
    it(s"docs/reference/stdlib.md's $enHeading section documents the extension-call spelling") {
      val doc = section(read("docs/reference/stdlib.md"), enHeading)
      val missing = extensionCalls.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's $enHeading section is missing extension-call spellings: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it(s"docs/ja/reference/stdlib.md's $jaHeading section documents the extension-call spelling") {
      val doc = section(read("docs/ja/reference/stdlib.md"), jaHeading)
      val missing = extensionCalls.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's $jaHeading section is missing extension-call spellings: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
