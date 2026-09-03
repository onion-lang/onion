/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;

/**
 * @author Kota Mizushima
 * Location in source code with optional span information.
 * @param line start line number in the source (1-origin)
 * @param column start column number in the source (1-origin)
 * @param endLine optional end line number for span (1-origin)
 * @param endColumn optional end column number for span (1-origin)
 */
/**
 * A source position, optionally with the end of the span it covers.
 *
 * The span end is stored as two plain ints (`-1` = absent) rather than `Option[Int]`s: a
 * location is allocated for every token and most AST nodes, and the boxed representation
 * was four objects per location (the location, a copy, two `Some`s), each a pointer to chase.
 * `endLine`/`endColumn` keep the `Option` view for the few readers that want it.
 */
final case class Location(
  line: Int,
  column: Int,
  endLineOrNone: Int = -1,
  endColumnOrNone: Int = -1
) {
  /** Java-compatible constructor with just line and column */
  def this(line: Int, column: Int) = this(line, column, -1, -1)

  def endLine: Option[Int] = if (endLineOrNone < 0) None else Some(endLineOrNone)
  def endColumn: Option[Int] = if (endColumnOrNone < 0) None else Some(endColumnOrNone)

  /** Returns true if this location has span information */
  def hasSpan: Boolean = endLineOrNone >= 0 && endColumnOrNone >= 0

  /** Returns the span length on a single line, or 1 if no span */
  def spanLength: Int = {
    if (hasSpan && endLineOrNone == line) {
      math.max(1, endColumnOrNone - column + 1)
    } else {
      1
    }
  }

  /** Creates a new Location with span information */
  def withSpan(endLine: Int, endColumn: Int): Location =
    Location(line, column, endLine, endColumn)

  /**
   * A location starting where this one starts and ending where `other` ends.
   *
   * Used to give a compound expression the extent of its parts: every leaf already spans
   * its own token, so `3 * "x"` can cover all five characters instead of putting a single
   * caret on the `*`. Java-friendly by design — the parser calls this, and unwrapping a
   * Scala `Option[Int]` from generated Java code is not.
   */
  def spanningTo(other: Location): Location =
    if (other == null) this
    else {
      val spanned = other.hasSpan
      val endL = if (spanned) other.endLineOrNone else other.line
      val endC = if (spanned) other.endColumnOrNone else other.column
      // A malformed pair -- an end before the start -- would make the renderer draw
      // nothing or worse; keep this location rather than produce one that lies.
      if (endL < line || (endL == line && endC < column)) this
      else Location(line, column, endL, endC)
    }
}

object Location {
  /** The former shape of the constructor, for callers that hold the span as options. */
  def apply(line: Int, column: Int, endLine: Option[Int], endColumn: Option[Int]): Location =
    Location(line, column, endLine.getOrElse(-1), endColumn.getOrElse(-1))
}
