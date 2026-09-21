package onion.compiler.tools

/**
 * docs/guide/inheritance.md has a "## Inheritance Best Practices" section
 * (three subsections: "### Favor Composition Over Inheritance" showing a
 * `PrefixLogger conforms Logger` + `forward val delegate: Logger` example,
 * "### Keep Hierarchies Shallow" with a good/bad ASCII hierarchy diagram,
 * and "### Override Consistently" with a Parent/Child `process` override
 * example). docs/ja/guide/inheritance.md jumps straight from
 * "## 抽象クラス" to "## 次のステップ" with no equivalent content at all,
 * so a Japanese-only reader had no way to learn any of this guidance
 * exists. This guards that the Japanese guide documents all three.
 */
class InheritanceDocBestPracticesParitySpec extends AbstractShellSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  describe("docs/ja/guide/inheritance.md") {
    it("has an Inheritance Best Practices section heading, like the English guide does") {
      val doc = read("docs/ja/guide/inheritance.md")
      assert(doc.contains("## 継承のベストプラクティス"),
        "docs/ja/guide/inheritance.md is missing the \"## Inheritance Best Practices\" section " +
        "present in docs/guide/inheritance.md (translated heading: \"## 継承のベストプラクティス\")")
    }

    it("documents keeping hierarchies shallow, like the English guide does") {
      val doc = read("docs/ja/guide/inheritance.md")
      assert(doc.contains("LuxurySedan"),
        "docs/ja/guide/inheritance.md is missing the \"Keep Hierarchies Shallow\" example " +
        "already present in docs/guide/inheritance.md's \"## Inheritance Best Practices\" section")
    }

    it("documents overriding consistently, like the English guide does") {
      val doc = read("docs/ja/guide/inheritance.md")
      assert(doc.contains("value * 3"),
        "docs/ja/guide/inheritance.md is missing the \"Override Consistently\" Parent/Child " +
        "example already present in docs/guide/inheritance.md's \"## Inheritance Best Practices\" section")
    }
  }
}
