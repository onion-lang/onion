package onion.compiler.tools

/**
 * docs/guide/classes-and-objects.md's "## Enums" section documents that
 * enums can declare methods in access sections after the constant list
 * ("instance methods see the constant's data, static methods see
 * `values()`"), with a worked `heavierThan` example — a real, tested
 * feature (`EnumMethodSpec`, "Enum methods" → "declares instance methods
 * using constant data"). It also shows `Planet::valueOf("EARTH")` right
 * after the `foreach`/`values()` example. docs/ja/guide/classes-and-objects.md's
 * "## Enums" section had neither: it jumped straight from the plain-constant
 * example to "### 代数的データ型（`case` case）", so a Japanese-only reader
 * had no way to learn enums can declare methods at all. This guards that
 * the Japanese guide documents both.
 */
class EnumMethodDocParitySpec extends AbstractShellSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  describe("docs/ja/guide/classes-and-objects.md") {
    it("documents that enums can declare methods, like the English guide does") {
      val doc = read("docs/ja/guide/classes-and-objects.md")
      assert(doc.contains("heavierThan"),
        "docs/ja/guide/classes-and-objects.md's Enums section is missing an example of an " +
        "enum declaring a method in an access section — already documented in " +
        "docs/guide/classes-and-objects.md's \"## Enums\" section via a `heavierThan` example, " +
        "and a real, tested feature (see EnumMethodSpec)")
    }

    it("documents Planet::valueOf, like the English guide does") {
      val doc = read("docs/ja/guide/classes-and-objects.md")
      assert(doc.contains("valueOf"),
        "docs/ja/guide/classes-and-objects.md's Enums section never mentions `valueOf` — " +
        "already documented in docs/guide/classes-and-objects.md's \"## Enums\" section " +
        "(`Planet::valueOf(\"EARTH\")`)")
    }
  }
}
