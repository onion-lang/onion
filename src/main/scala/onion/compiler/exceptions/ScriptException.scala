/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.exceptions

/**
 * This class represents an exception while script is running.
 *
 * @author Kota Mizushima
 * Date: 2005/09/15
 */
class ScriptException(message: String, cause: Throwable) extends RuntimeException(message, cause) {
  def this() {
    this("", null)
  }

  /**
   * @param cause
   */
  def this(cause: Throwable) {
    this("", cause)
  }
}
