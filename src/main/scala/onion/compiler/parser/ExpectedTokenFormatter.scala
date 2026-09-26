package onion.compiler.parser

import scala.collection.mutable.LinkedHashSet

private[compiler] object ExpectedTokenFormatter {
  private val Fallback = "valid token"

  /** Render the first token of each JavaCC expected-token sequence. */
  /**
   * Every expected token, untruncated, for the hint classifier. The user-facing `format`
   * shows the first four, and which four those are depends on the order JavaCC recorded
   * its choice points in -- a detail a grammar change moves around, and one no hint
   * should hinge on.
   */
  def formatAll(expectedTokenSequences: Array[Array[Int]], tokenImages: Array[String]): String = {
    if (expectedTokenSequences == null || expectedTokenSequences.isEmpty) return Fallback
    val expectedSet = LinkedHashSet[String]()
    for (sequence <- expectedTokenSequences)
      if (sequence != null && sequence.nonEmpty) expectedSet += tokenImages(sequence(0))
    if (expectedSet.isEmpty) Fallback else expectedSet.mkString(", ")
  }

  def format(expectedTokenSequences: Array[Array[Int]], tokenImages: Array[String]): String = {
    if (expectedTokenSequences == null || expectedTokenSequences.isEmpty) {
      return Fallback
    }

    val expectedSet = LinkedHashSet[String]()
    for (sequence <- expectedTokenSequences) {
      if (sequence != null && sequence.nonEmpty) {
        expectedSet += tokenImages(sequence(0))
      }
    }

    render(expectedSet.toSeq)
  }

  /** The same two renderings, over a plain kind list (the handwritten parser's
   *  CollectedError.expectedKinds) instead of JavaCC's expected-token sequences. */
  def formatKinds(kinds: Seq[Int], tokenImages: Array[String]): String =
    render(LinkedHashSet.from(kinds.map(tokenImages(_))).toSeq)

  def formatAllKinds(kinds: Seq[Int], tokenImages: Array[String]): String = {
    val images = LinkedHashSet.from(kinds.map(tokenImages(_)))
    if (images.isEmpty) Fallback else images.mkString(", ")
  }

  private def render(expected: Seq[String]): String =
    if (expected.isEmpty) {
      Fallback
    } else if (expected.size == 1) {
      expected.head
    } else if (expected.size <= 3) {
      expected.init.mkString(", ") + " or " + expected.last
    } else {
      val shown = expected.take(4)
      shown.mkString(", ") + s", ... (${expected.size - shown.size} more)"
    }
}
