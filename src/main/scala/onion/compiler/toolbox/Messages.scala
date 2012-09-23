/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox

import java.text.MessageFormat
import java.util.ResourceBundle

/**
 * @author Kota Mizushima
 * Date: 2005/06/17
 */
object Messages {
  private[this] val ERROR_MESSAGES: ResourceBundle = ResourceBundle.getBundle("errorMessage")

  def apply(property: String): String =  ERROR_MESSAGES.getString(property)

  def apply(property: String, arguments: Array[Any]): String =  MessageFormat.format(apply(property), arguments.map(_.asInstanceOf[AnyRef]):_*)

  def apply(property: String, arg1: Any): String = MessageFormat.format(apply(property), arg1.asInstanceOf[AnyRef])

  def apply(property: String, arg1: Any, arg2: Any): String =  MessageFormat.format(apply(property), arg1.asInstanceOf[AnyRef], arg2.asInstanceOf[AnyRef])
}
