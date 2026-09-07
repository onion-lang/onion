package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the Basic/OOP/Functional subsection links in docs/ja/examples/index.md.
 * docs/examples/overview.md links those nine bullets straight into the relevant heading
 * (e.g. `basic.md#user-input`), but the Japanese counterpart used to link to the bare page
 * with no anchor at all, landing the reader at the top of basic.md/oop.md/functional.md
 * instead of the section the bullet names.
 */
class ExamplesIndexAnchorParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  // Mirrors mkdocs' default toc slugify: lowercase, drop everything but letters/digits/
  // underscore/space/hyphen, collapse whitespace and hyphens into a single hyphen.
  private def slugify(heading: String): String = {
    val cleaned = heading.trim.toLowerCase.replaceAll("[^\\p{L}\\p{Nd}_\\s-]", "")
    cleaned.replaceAll("[\\s-]+", "-")
  }

  private def headingSlugs(doc: String): Set[String] =
    """(?m)^##\s+(.+?)\s*$""".r.findAllMatchIn(doc).map(m => slugify(m.group(1))).toSet

  private val expectedAnchors = Seq(
    "basic.md#hello-world",
    "basic.md#配列",
    "basic.md#ユーザー入力",
    "oop.md#クラスとオブジェクト",
    "oop.md#継承",
    "oop.md#インターフェース",
    "functional.md#ラムダ式",
    "functional.md#クロージャ",
    "functional.md#再帰"
  )

  it("links its Basic/OOP/Functional bullets to a heading anchor, matching the English overview") {
    val ja = read("docs/ja/examples/index.md")
    val missing = expectedAnchors.filterNot(ja.contains)
    assert(missing.isEmpty,
      s"docs/ja/examples/index.md is missing anchored links present in the English overview: " +
      missing.mkString(", "))
  }

  it("every expected anchor resolves to an actual heading in its target page") {
    val targets = Map(
      "basic.md" -> headingSlugs(read("docs/ja/examples/basic.md")),
      "oop.md" -> headingSlugs(read("docs/ja/examples/oop.md")),
      "functional.md" -> headingSlugs(read("docs/ja/examples/functional.md"))
    )
    for (anchor <- expectedAnchors) {
      val Array(file, slug) = anchor.split("#", 2)
      assert(targets(file).contains(slug),
        s"docs/ja/examples/$file has no heading that slugifies to '$slug' (from anchor $anchor)")
    }
  }
}
