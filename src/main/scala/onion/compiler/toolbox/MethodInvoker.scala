/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * @author Kota Mizushima
 *         Date: 2005/09/15
 */
object MethodInvoker {
  def call(target: AnyRef, name: String, args: Array[AnyRef]): AnyRef = {
    try {
      getMethod(target.getClass, name, args).invoke(target, args)
    } catch {
      case e: NoSuchMethodException => {
        throw new InvocationException(e)
      }
      case e: IllegalArgumentException => {
        throw new InvocationException(e)
      }
      case e: IllegalAccessException => {
        throw new InvocationException(e)
      }
      case e: InvocationTargetException => {
        throw new InvocationException(e)
      }
    }
  }

  def callStatic(target: Class[_], name: String, args: Array[AnyRef]): AnyRef = {
    try {
      getMethod(target, name, args).invoke(null, args)
    }
    catch {
      case e: NoSuchMethodException => {
        throw new InvocationException(e)
      }
      case e: IllegalArgumentException => {
        throw new InvocationException(e)
      }
      case e: IllegalAccessException => {
        throw new InvocationException(e)
      }
      case e: InvocationTargetException => {
        throw new InvocationException(e)
      }
    }
  }

  private def getMethod(target: Class[_], name: String, args: Array[AnyRef]): Method = {
    val argsClasses: Array[Class[_]] = new Array[Class[_]](args.length)
    var i: Int = 0
    while (i < args.length) {
      argsClasses(i) = args(i).getClass
      i += 1
    }
    target.getMethod(name, argsClasses:_*)
  }
}
