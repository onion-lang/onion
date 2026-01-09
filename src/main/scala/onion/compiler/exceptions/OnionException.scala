/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.exceptions

/**
 * Base exception class for all Onion compiler and runtime exceptions.
 *
 * @author Kota Mizushima
 */
abstract class OnionException(message: String, cause: Throwable) extends RuntimeException(message, cause) {
  def this(message: String) = this(message, null)
  def this(cause: Throwable) = this(if (cause != null) cause.getMessage else null, cause)
  def this() = this(null, null)
}
