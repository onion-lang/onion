package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard tying the "retired codes" note in `SemanticError.scala`'s class-level
 * scaladoc to the numbering gaps the file actually contains. The note undercounted
 * by one (only mentioning E0017 and E0024) after E0056 was deleted, so a future
 * deletion or restoration of a code can silently leave the note wrong again without
 * this test catching it.
 */
class SemanticErrorRetiredCodesDocSpec extends AnyFunSpec {

  private lazy val source: String =
    java.nio.file.Files.readString(
      java.nio.file.Path.of("src/main/scala/onion/compiler/SemanticError.scala")
    )

  private lazy val declaredCodes: Set[Int] =
    """case object \w+ extends SemanticError\((\d+)\)""".r
      .findAllMatchIn(source)
      .map(_.group(1).toInt)
      .toSet

  private lazy val actualGaps: Set[Int] = {
    assert(declaredCodes.nonEmpty, "no SemanticError cases found -- the scan has rotted")
    (declaredCodes.min to declaredCodes.max).toSet -- declaredCodes
  }

  private lazy val notedGaps: Set[Int] = {
    val noteLine = source.linesIterator
      .find(l => l.contains("retired codes") && l.contains("kept unassigned"))
      .getOrElse(fail("no \"retired codes, kept unassigned\" note found in SemanticError.scala"))
    """E(\d{4})""".r.findAllMatchIn(noteLine).map(_.group(1).toInt).toSet
  }

  it("names every numbering gap left by a retired SemanticError code, and no other") {
    assert(
      notedGaps == actualGaps,
      s"note lists $notedGaps but the actual gaps in the SemanticError(N) numbering are $actualGaps"
    )
  }
}
