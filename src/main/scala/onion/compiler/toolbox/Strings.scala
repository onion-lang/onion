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
 *         Date: 2005/06/22
 */
object Strings {
  def join(array: Array[String], separator: String): String = {
    if (array.length == 0) return ""
    val buffer: StringBuffer = new StringBuffer
    var i: Int = 0
    while (i < array.length - 1) {
      buffer.append(array(i))
      buffer.append(separator)
      i += 1
    }
    buffer.append(array(array.length - 1))
    new String(buffer)
  }

  def append(strings1: Array[String], strings2: Array[String]): Array[String] = {
    val newStrings: Array[String] = new Array[String](strings1.length + strings2.length)
    System.arraycopy(strings1, 0, newStrings, 0, strings1.length)
    System.arraycopy(strings2, 0, newStrings, strings1.length, strings2.length)
    newStrings
  }

  def repeat(source: String, times: Int): String = {
    val buffer = new StringBuffer
    var i: Int = 0
    while (i < times) {
      buffer.append(source)
      i += 1
    }
    new String(buffer)
  }
}
