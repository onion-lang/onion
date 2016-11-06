/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox

import java.io.File

/**
 * @author Kota Mizushima
 *        
 */
object Paths {
  def nameOf(path: String): String = new File(path).getName

  def cutExtension(path: String): String = {
    val name = nameOf(path)
    name.lastIndexOf('.') match {
      case n if n < 0 => name
      case n => name.substring(0, n)
    }
  }
}
