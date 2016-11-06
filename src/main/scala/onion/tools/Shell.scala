/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
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

class Shell (val classLoader: ClassLoader, val classpath: Seq[String]) {
  private val config = new CompilerConfig(classpath, null, "Shift_JIS", "", 10)
  def run(script: String, fileName: String, args: Array[String]): Shell.Result = {
    val compiler: OnionCompiler = new OnionCompiler(config)
    Thread.currentThread.setContextClassLoader(classLoader)
    val classes: Array[CompiledClass] = compiler.compile(Array[InputSource](new StreamInputSource(new StringReader(script), fileName)))
    run(classes, args)
  }

  def run(classes: Array[CompiledClass], args: Array[String]): Shell.Result = {
    try {
      val loader = new OnionClassLoader(classLoader, classpath, classes)
      Thread.currentThread.setContextClassLoader(loader)
      val main = findFirstMainMethod(loader, classes)
      main match {
        case Some(method) => Shell.Success(method.invoke(null, args))
        case None => Shell.Failure(-1)
      }
    } catch {
      case _: ClassNotFoundException | _: IllegalAccessException | _: MalformedURLException => Shell.Failure(-1)
      case e: InvocationTargetException => throw new ScriptException(e.getCause)
    }
  }

  private def findFirstMainMethod(loader: OnionClassLoader, classes: Array[CompiledClass]): Option[Method] = {
    for (i <- 0 until classes.length) {
      val className = classes(i).getClassName
      val clazz = Class.forName(className, true, loader)
      try {
        val main = clazz.getMethod("main", classOf[Array[String]])
        val modifier = main.getModifiers
        if ((modifier & Modifier.PUBLIC) != 0 && (modifier & Modifier.STATIC) != 0) {
          return Some(main)
        }
      } catch {
        case _: NoSuchMethodException =>
      }
    }
    None
  }

}

object Shell {
  def apply(classpath: Seq[String]): Shell = {
    new Shell(classOf[OnionClassLoader].getClassLoader, classpath)
  }
  sealed abstract class Result
  case class Success(value: Any) extends Result
  case class Failure(code: Int) extends Result
}

