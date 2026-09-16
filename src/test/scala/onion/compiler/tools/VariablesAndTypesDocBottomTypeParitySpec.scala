package onion.compiler.tools

/**
 * docs/guide/variables-and-types.md documents the bottom type (`Nothing`, the
 * subtype of all types, used for expressions that never return such as `return`,
 * `throw`, `break`, and `continue` — a real, tested type, see
 * `TypedAST.BOTTOM`/`TypedAST.BottomType`) in a "### Bottom Type (Nothing)"
 * subsection under "## Type System". docs/ja/guide/variables-and-types.md's
 * equivalent "## 型システム" section never mentions `Nothing` at all, so a
 * Japanese-only reader has no way to learn the concept exists. This guards that
 * the Japanese guide documents it too.
 */
class VariablesAndTypesDocBottomTypeParitySpec extends AbstractShellSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    val rest = lines.drop(start + 1)
    val end = rest.indexWhere(l => l.trim.startsWith("#") && l.trim.count(_ == '#') <= heading.takeWhile(_ == '#').length)
    (if (end >= 0) rest.take(end) else rest).mkString("\n")
  }

  describe("docs/ja/guide/variables-and-types.md 型システム section") {
    it("documents the bottom type (Nothing), like the English guide does") {
      val doc = read("docs/ja/guide/variables-and-types.md")
      val section = sectionUnder(doc, "## 型システム")
      assert(section.contains("Nothing"),
        "docs/ja/guide/variables-and-types.md's 型システム section is missing any mention of the bottom " +
        "type `Nothing` — already documented in docs/guide/variables-and-types.md's " +
        "\"### Bottom Type (Nothing)\" subsection")
      assert(
        section.contains("return") && section.contains("throw") &&
        section.contains("break") && section.contains("continue"),
        "the ja Nothing/bottom-type mention should explain it's used for expressions that never return " +
        "(return/throw/break/continue), like the English guide does"
      )
    }
  }
}
