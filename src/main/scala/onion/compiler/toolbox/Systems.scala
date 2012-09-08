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
  def lineSeparator: String = System.getProperty("line.separator")

  def lineSeparatorTimes(count: Int): String = {
    val separator = lineSeparator
    List.fill(count)(separator).mkString("")
  }

  def pathSeparator: String = System.getProperty("path.separator")

  def fileSeparator: String = System.getProperty("file.separator")
}
