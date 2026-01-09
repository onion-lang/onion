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
import onion.compiler.CompilationOutcome
import onion.compiler.CompilationOutcome.{Failure, Success}
import onion.compiler.CompilationReporter
import onion.compiler.exceptions.ScriptException

class Shell (val classLoader: ClassLoader, val classpath: Seq[String]) {
  private val encoding = Option(System.getenv("ONION_ENCODING"))
    .getOrElse(java.nio.charset.Charset.defaultCharset().name())
  private val config = new CompilerConfig(classpath, null, encoding, "", 10)
  def run(script: String, fileName: String, args: Array[String]): Shell.Result = {
    val compiler: OnionCompiler = new OnionCompiler(config)
    val outcome: CompilationOutcome = withContextClassLoader(classLoader) {
      compiler.compile(Seq(new StreamInputSource(new StringReader(script), fileName)))
    }
    outcome match {
      case Success(classes) => run(classes, args)
      case Failure(errors) =>
        CompilationReporter.printErrors(errors)
        Shell.Failure(-1)
    }
  }

  def run(classes: Seq[CompiledClass], args: Array[String]): Shell.Result = {
    val loader = new OnionClassLoader(classLoader, classpath, classes)
    withContextClassLoader(loader) {
      try {
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
  }

  private def findFirstMainMethod(loader: OnionClassLoader, classes: Seq[CompiledClass]): Option[Method] = {
    for (i <- 0 until classes.length) {
      val className = classes(i).className
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

  private def withContextClassLoader[T](loader: ClassLoader)(body: => T): T = {
    val thread = Thread.currentThread
    val previous = thread.getContextClassLoader
    thread.setContextClassLoader(loader)
    try body
    finally thread.setContextClassLoader(previous)
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
