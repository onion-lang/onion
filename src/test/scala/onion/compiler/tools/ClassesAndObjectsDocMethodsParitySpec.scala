package onion.compiler.tools

/**
 * docs/guide/classes-and-objects.md documents instance methods, method
 * overloading, getter/setter methods (under "## Methods") and the `this`
 * reference (under "## The `this` Reference", which also notes that `self` is
 * retired, RFC #2788). docs/ja/guide/classes-and-objects.md once had no
 * counterpart for either section. This guards that the Japanese guide documents
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

    it("documents the `this` reference and the retired `self`, like the English guide does") {
      val doc = read("docs/ja/guide/classes-and-objects.md")
      assert(doc.contains("## `this` 参照"),
        "docs/ja/guide/classes-and-objects.md has no `this` reference section — already documented in " +
        "docs/guide/classes-and-objects.md's \"## The `this` Reference\" section")
      assert(doc.contains("E0117"),
        "the ja `this` section should say that `self` is retired (E0117), like the English guide does")
      assert(doc.contains("static"),
        "the ja `this` section should note that `this` is unavailable in static " +
        "contexts, like the English guide does")
    }
  }
}
