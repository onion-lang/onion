package onion.compiler

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the "Error Categories" scaladoc block atop `SemanticError`.
 *
 * That block sorts every declared code into a category (Type Errors, Resolution
 * Errors, ...) via a curated bullet list, but nothing ever tied it to the `case
 * object` declarations below it. 23 of the 89 declared codes -- including recent
 * additions like `CLASS_USED_AS_VALUE` and `NULLABLE_MEMBER_ACCESS` -- were entirely
 * absent from the categorization, and one listed range (`E0016-E0018`) included a
 * code, E0017, that was removed and never reused. This ties the doc block to the
 * `case object` list so a newly added code can't go uncategorized silently.
 */
class SemanticErrorCategoryDocCoverageSpec extends AnyFunSpec {

  private lazy val source: String =
    java.nio.file.Files.readString(
      java.nio.file.Path.of("src/main/scala/onion/compiler/SemanticError.scala")
    )

  private lazy val declaredNames: Set[String] =
    """case object (\w+) extends SemanticError\(\d+\)""".r
      .findAllMatchIn(source)
      .map(_.group(1))
      .toSet

  private lazy val categoryDoc: String =
    source.split("object SemanticError \\{", 2)(0)

  it("declares at least one SemanticError code (the scan has not rotted)") {
    assert(declaredNames.nonEmpty)
  }

  it("mentions every declared SemanticError code in its Error Categories doc block") {
    val missing = declaredNames.filterNot(categoryDoc.contains).toSeq.sorted
    assert(missing.isEmpty, s"Error Categories doc block does not mention: ${missing.mkString(", ")}")
  }
}
