/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * @author Kota Mizushima
 *
 */
object MethodInvoker {
  def call(target: AnyRef, name: String, args: Array[AnyRef]): AnyRef = {
    try {
      getMethod(target.getClass, name, args).invoke(target, args)
    } catch {
      case e: NoSuchMethodException =>
        throw new InvocationException(e)
      case e: IllegalArgumentException =>
        throw new InvocationException(e)
      case e: IllegalAccessException =>
        throw new InvocationException(e)
      case e: InvocationTargetException =>
        throw new InvocationException(e)
    }
  }

  def callStatic(target: Class[_], name: String, args: Array[AnyRef]): AnyRef = {
    try {
      getMethod(target, name, args).invoke(null, args)
    }
    catch {
      case e: NoSuchMethodException =>
        throw new InvocationException(e)
      case e: IllegalArgumentException =>
        throw new InvocationException(e)
      case e: IllegalAccessException =>
        throw new InvocationException(e)
      case e: InvocationTargetException =>
        throw new InvocationException(e)
    }
  }

  private def getMethod(target: Class[_], name: String, args: Array[AnyRef]): Method = {
    val argsClasses = args.map {_.getClass()}
    target.getMethod(name, argsClasses:_*)
  }
}
