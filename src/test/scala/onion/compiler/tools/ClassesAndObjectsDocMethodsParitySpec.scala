package onion.compiler.tools

/**
 * docs/guide/classes-and-objects.md documents instance methods, method
 * overloading, getter/setter methods (under "## Methods") and the `self`
 * reference (under "## The `self` Reference") — real, tested features (`self`
 * is a reserved-word alias for `this`, see `SelfReferentialInitializerSpec`,
 * `SelfReferentialBoundSpec`). docs/ja/guide/classes-and-objects.md had no
 * counterpart for either section, so a Japanese-only reader had no way to
 * learn `self` exists at all. This guards that the Japanese guide documents
 * both.
 */
class ClassesAndObjectsDocMethodsParitySpec extends AbstractShellSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  describe("docs/ja/guide/classes-and-objects.md") {
    it("documents a Methods section, like the English guide does") {
      val doc = read("docs/ja/guide/classes-and-objects.md")
      assert(doc.contains("## メソッド"),
        "docs/ja/guide/classes-and-objects.md is missing a Methods section — " +
        "already documented in docs/guide/classes-and-objects.md's \"## Methods\" section " +
        "(instance methods, overloading, getters/setters)")
      assert(doc.contains("オーバーロード"),
        "the ja Methods section should mention method overloading, like the English guide's " +
        "\"### Method Overloading\" subsection")
    }

    it("documents the `self` reference, like the English guide does") {
      val doc = read("docs/ja/guide/classes-and-objects.md")
      assert(doc.contains("self"),
        "docs/ja/guide/classes-and-objects.md never mentions `self` — already documented in " +
        "docs/guide/classes-and-objects.md's \"## The `self` Reference\" section, and `self` is a " +
        "real, tested reserved-word alias for `this`")
      assert(doc.contains("static"),
        "the ja self-reference mention should note that `this`/`self` are unavailable in static " +
        "contexts, like the English guide does")
    }
  }
}
