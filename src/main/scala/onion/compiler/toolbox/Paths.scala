/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox

import java.io.File

/**
 * @author Kota Mizushima
 *         Date: 2005/06/17
 */
object Paths {
  def nameOf(path: String): String = new File(path).getName

  def cutExtension(path: String): String = {
    val name = nameOf(path)
    name.substring(0, name.lastIndexOf('.'))
  }
}
