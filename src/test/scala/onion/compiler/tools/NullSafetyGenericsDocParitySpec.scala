package onion.compiler.tools

/**
 * docs/guide/null-safety.md's "## Nullable-Aware Generics" section documents
 * four real, tested behaviors: a bare `[T]` accepts nullable type arguments but
 * restricts dereferencing inside the generic body (E0057, see
 * `NullableGenericsSpec`), `[T extends B]` requires a non-null argument,
 * `[T extends B?]` opts back into nullable with a bound (also
 * `NullableGenericsSpec`), and type-argument inference merges to a nullable
 * type when the arguments require it (`NullableGenericArgSpec`,
 * `NullableTypeParamInferenceSpec`). docs/ja/guide/null-safety.md's
 * "## Nullable対応ジェネリクス" section was a one-paragraph stub mentioning only
 * the first two of these — a Japanese-only reader had no way to learn that
 * `[T extends B?]` syntax exists, that dereferencing a bare `T` inside the body
 * is restricted, or how inference merges nullability. This guards that the
 * Japanese section covers all four, like the English section does.
 */
class NullSafetyGenericsDocParitySpec extends AbstractShellSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def sectionUnder(doc: String, heading: String): String = {
    val lines = doc.linesIterator.toSeq
    val start = lines.indexWhere(_.trim == heading)
    assert(start >= 0, s"could not find heading '$heading' — the scan has rotted")
    val rest = lines.drop(start + 1)
    val end = rest.indexWhere(l => l.trim.startsWith("#") && !l.trim.startsWith("###"))
    (if (end >= 0) rest.take(end) else rest).mkString("\n")
  }

  describe("docs/ja/guide/null-safety.md") {
    it("documents that a bare [T] restricts dereferencing inside the generic body (E0057)") {
      val section = sectionUnder(read("docs/ja/guide/null-safety.md"), "## Nullable対応ジェネリクス")
      assert(section.contains("E0057"),
        "the ja Nullable-generics section should mention E0057, like the English guide's note that " +
        "values of a bare T cannot be dereferenced directly inside the generic body")
    }

    it("documents the [T extends B?] syntax") {
      val section = sectionUnder(read("docs/ja/guide/null-safety.md"), "## Nullable対応ジェネリクス")
      assert(section.contains("extends B?") || section.contains("extends Comparable?"),
        "the ja Nullable-generics section is missing the `[T extends B?]` syntax that opts back into " +
        "nullable with a bound, already documented in the English guide's " +
        "\"### `[T extends B?]` opts back into nullable with a bound\" subsection")
    }

    it("documents that type-argument inference merges to a nullable type") {
      val section = sectionUnder(read("docs/ja/guide/null-safety.md"), "## Nullable対応ジェネリクス")
      assert(section.contains("推論"),
        "the ja Nullable-generics section is missing an inference subsection, already documented in " +
        "the English guide's \"### Inference\" subsection (merges to nullable when arguments require it)")
    }
  }
}
