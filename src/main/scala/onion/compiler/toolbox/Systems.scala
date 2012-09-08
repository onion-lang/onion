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
  lazy val lineSeparator: String = System.getProperty("line.separator")

  def lineSeparatorTimes(count: Int): String = {
    val separator = lineSeparator
    List.fill(count)(separator).mkString("")
  }

  lazy val pathSeparator: String = System.getProperty("path.separator")

  lazy val fileSeparator: String = System.getProperty("file.separator")
}
