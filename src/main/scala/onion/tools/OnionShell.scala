/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools

import java.io.StringReader
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.MalformedURLException
import onion.compiler._
import onion.compiler.exceptions.ScriptException

class OnionShell {
  def this(classLoader: ClassLoader, classpath: Array[String]) {
    this()
    this.classLoader = classLoader
    this.classpath = classpath
  }

  def run(script: String, fileName: String, args: Array[String]): Int = {
    val compiler: OnionCompiler = new OnionCompiler(new CompilerConfig(classpath, null, "Shift_JIS", "", 10))
    Thread.currentThread.setContextClassLoader(classLoader)
    val classes: Array[CompiledClass] = compiler.compile(Array[InputSource](new StreamInputSource(new StringReader(script), fileName)))
    return run(classes, args)
  }

  def run(classes: Array[CompiledClass], args: Array[String]): Int = {
    try {
      val loader: OnionClassLoader = new OnionClassLoader(classLoader, classpath, classes)
      Thread.currentThread.setContextClassLoader(loader)
      val main: Method = findFirstMainMethod(loader, classes)
      if (main == null) {
        return -1
      }
      else {
        main.invoke(null, args)
        return 0
      }
    }
    catch {
      case e: ClassNotFoundException => {
        return -1
      }
      case e: IllegalAccessException => {
        return -1
      }
      case e: MalformedURLException => {
        return -1
      }
      case e: InvocationTargetException => {
        throw new ScriptException(e.getCause)
      }
    }
  }

  private def findFirstMainMethod(loader: OnionClassLoader, classes: Array[CompiledClass]): Method = {
    {
      var i: Int = 0
      while (i < classes.length) {
        {
          val className: String = classes(i).getClassName
          val clazz: Class[_] = Class.forName(className, true, loader)
          try {
            val main: Method = clazz.getMethod("main", classOf[Array[String]])
            val modifier: Int = main.getModifiers
            if ((modifier & Modifier.PUBLIC) != 0 && (modifier & Modifier.STATIC) != 0) {
              return main
            }
          }
          catch {
            case e1: NoSuchMethodException => {
            }
          }
        }
        ({
          i += 1;
          i
        })
      }
    }
    return null
  }

  private var classLoader: ClassLoader = null
  private var classpath: Array[String] = null
}