/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox

/**
 * @author Kota Mizushima
 *         Date: 2005/06/26
 */
object Systems {
  def getLineSeparator: String = System.getProperty("line.separator")

  def getLineSeparator(count: Int): String = {
    val separator: String = getLineSeparator
    val separators: StringBuffer = new StringBuffer
    var i: Int = 0
    while (i < count) {
      separators.append(separator)
      i += 1
    }
    new String(separators)
  }

  def getPathSeparator: String = System.getProperty("path.separator")

  def getFileSeparator: String = System.getProperty("file.separator")
}
