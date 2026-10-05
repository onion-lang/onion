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
import onion.compiler.diagnostics.DiagnosticRenderer
import onion.compiler.exceptions.ScriptException
import onion.compiler.toolbox.Message

class Shell (val classLoader: ClassLoader, val classpath: Seq[String]) {
  private val encoding = Option(System.getenv("ONION_ENCODING"))
    .getOrElse(java.nio.charset.Charset.defaultCharset().name())
  private val config = new CompilerConfig(classpath, null, encoding, "", 10)
  def run(script: String, fileName: String, args: Array[String]): Shell.Result = {
    val compiler: OnionCompiler = new OnionCompiler(config)
    val result = withContextClassLoader(classLoader) {
      compiler.compileDetailed(Seq(new StreamInputSource(() => new StringReader(script), fileName)))
    }
    if (result.hasErrors) {
      DiagnosticRenderer.printDiagnostics(result.diagnostics)
      Shell.Failure(-1)
    } else {
      run(result.classes, args)
    }
  }

  /** Compile-only check: true if the script compiles with no errors (does NOT
   *  run it). Used by the sample-corpus regression test so a sample that stops
   *  compiling is caught even if it would read stdin or loop when run. */
  def compiles(script: String, fileName: String): Boolean = {
    val compiler: OnionCompiler = new OnionCompiler(config)
    val result = withContextClassLoader(classLoader) {
      compiler.compileDetailed(Seq(new StreamInputSource(() => new StringReader(script), fileName)))
    }
    !result.hasErrors
  }

  def run(classes: Seq[CompiledClass], args: Array[String]): Shell.Result = {
    val loader = new OnionClassLoader(classLoader, classpath, classes)
    withContextClassLoader(loader) {
      try {
        findFirstMainMethod(loader, classes) match {
          case Right(method) => Shell.Success(method.invoke(null, args))
          case Left(Some(nonPublicClassName)) =>
            System.err.println(Message("error.command.noEntryPoint.notPublic", nonPublicClassName))
            Shell.Failure(-1)
          case Left(None) =>
            val classNames = classes.map(_.className).mkString(", ")
            System.err.println(Message("error.command.noEntryPoint", classNames))
            Shell.Failure(-1)
        }
      } catch {
        case _: ClassNotFoundException | _: IllegalAccessException | _: MalformedURLException => Shell.Failure(-1)
        case e: InvocationTargetException => throw new ScriptException(e.getCause)
      }
    }
  }

  /** `Right` the public static entry point if one exists. Otherwise `Left`:
   *  `Some(className)` naming a class whose `main(String[])` exists but is not
   *  public (so the generic "no entry point found" message can be replaced with
   *  one that names the actual cause), or `None` if no class declares `main`
   *  at all. */
  private def findFirstMainMethod(loader: OnionClassLoader, classes: Seq[CompiledClass]): Either[Option[String], Method] = {
    val publicMain = classes.view.flatMap { compiled =>
      val clazz = Class.forName(compiled.className, true, loader)
      try {
        val main = clazz.getMethod("main", classOf[Array[String]])
        val modifier = main.getModifiers
        if ((modifier & Modifier.PUBLIC) != 0 && (modifier & Modifier.STATIC) != 0) Some(main) else None
      } catch {
        case _: NoSuchMethodException => None
      }
    }.headOption
    publicMain match {
      case Some(method) => Right(method)
      case None =>
        val nonPublicStaticMain = classes.view.flatMap { compiled =>
          val clazz = Class.forName(compiled.className, true, loader)
          try {
            val main = clazz.getDeclaredMethod("main", classOf[Array[String]])
            val modifier = main.getModifiers
            if ((modifier & Modifier.STATIC) != 0 && (modifier & Modifier.PUBLIC) == 0) Some(compiled.className) else None
          } catch {
            case _: NoSuchMethodException => None
          }
        }.headOption
        Left(nonPublicStaticMain)
    }
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
