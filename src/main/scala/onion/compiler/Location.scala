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
final case class Location(
  line: Int,
  column: Int,
  endLine: Option[Int] = None,
  endColumn: Option[Int] = None
) {
  /** Java-compatible constructor with just line and column */
  def this(line: Int, column: Int) = this(line, column, None, None)

  /** Returns true if this location has span information */
  def hasSpan: Boolean = endLine.isDefined && endColumn.isDefined

  /** Returns the span length on a single line, or 1 if no span */
  def spanLength: Int = {
    if (hasSpan && endLine.contains(line)) {
      math.max(1, endColumn.get - column + 1)
    } else {
      1
    }
  }

  /** Creates a new Location with span information */
  def withSpan(endLine: Int, endColumn: Int): Location =
    copy(endLine = Some(endLine), endColumn = Some(endColumn))
}
