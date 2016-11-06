/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox

import java.io._
import java.io.BufferedReader

/**
 * Utility class for IO.
 * @author Kota Mizushima
 *
 */
object Inputs {
  def newReader(path: String): BufferedReader = new BufferedReader(new FileReader(new File(path)))

  def newReader(path: String, encoding: String): BufferedReader = {
    new BufferedReader(new InputStreamReader(new FileInputStream(new File(path)), encoding))
  }

  def newWriter(path: String): PrintWriter = {
    new PrintWriter(new BufferedWriter(new FileWriter(path)))
  }

  def newInputStream(path: String): BufferedInputStream = {
    new BufferedInputStream(new FileInputStream(path))
  }

  def newOutputStream(path: String): BufferedOutputStream = {
    new BufferedOutputStream(new FileOutputStream(path))
  }
}
