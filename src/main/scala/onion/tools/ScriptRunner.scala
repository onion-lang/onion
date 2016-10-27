/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools

import java.io.UnsupportedEncodingException
import java.lang.System.err
import onion.compiler._
import onion.compiler.exceptions.ScriptException
import onion.compiler.toolbox.Messages
import onion.compiler.toolbox.Systems
import onion.tools.option._

/**
 *
 * @author Kota Mizushima
 *
 */
object ScriptRunner {
  private def conf(optionName: String, requireArgument: Boolean) = OptionConfig(optionName, requireArgument)

  private def pathArray(path: String): Array[String] =  path.split(Systems.pathSeparator)

  def main(args: Array[String]) {
    try {
      new ScriptRunner().run(args)
    }
    catch {
      case e: ScriptException => {
        throw e.getCause
      }
    }
  }

  private final val CLASSPATH: String = "-classpath"
  private final val SCRIPT_SUPER_CLASS: String = "-super"
  private final val ENCODING: String = "-encoding"
  private final val MAX_ERROR: String = "-maxErrorReport"
  private final val DEFAULT_CLASSPATH: Array[String] = Array[String](".")
  private final val DEFAULT_ENCODING: String = System.getProperty("file.encoding")
  private final val DEFAULT_OUTPUT: String = "."
  private final val DEFAULT_MAX_ERROR: Int = 10
}

class ScriptRunner {
  import ScriptRunner._
  private[this] val parser = new CommandLineParser(conf(CLASSPATH, true), conf(SCRIPT_SUPER_CLASS, true), conf(ENCODING, true), conf(MAX_ERROR, true))

  def run(commandLine: Array[String]): Int = {
    if (commandLine.length == 0) {
      printUsage()
      return -1
    }
    parser.parse(commandLine) match {
      case failure@ParseFailure(_, _) =>
        printFailure(failure)
        return -1
      case success@ParseSuccess(_, _) =>
        val config = createConfig(success)
        if (config.isEmpty) return -1
        createConfig(success) match {
          case None => return - 1
          case Some (config) =>
            val params = success.arguments
            if(params.length == 0) {
              printUsage()
              return -1
            }
            val scriptParams: Array[String] = new Array[String](params.length - 1)
            val classes = compile(config, Array(params(0)))
            if(classes == null) return -1
            for(i <- 1 until params.length) {
              scriptParams(i - 1) = params(i)
            }
            new Shell(classOf[OnionClassLoader].getClassLoader, config.classPath).run(classes, scriptParams)
        }
    }
  }

  protected def printUsage(): Unit = {
    err.println("""Usage: onion [-options] <source file> <command line arguments>
                  |options:
                  |-super <super class>        specify script's super class
                  | -classpath <class path>     specify classpath
                  | -encoding <encoding>        specify source file encoding
                  | -maxErrorReport <number>    set number of errors reported""".stripMargin)
  }

  private def printFailure(failure: ParseFailure): Unit = {
    failure.lackedOptions.zipWithIndex.foreach{ case (lackedOption, i) =>
      err.println(Messages("error.command.noArgument", lackedOption))
    }
  }

  private def createConfig(result: ParseSuccess): Option[CompilerConfig] = {
    val option: Map[String, CommandLineParam] = result.options.toMap
    val classpath: Array[String] = checkClasspath(option.get(CLASSPATH).asInstanceOf[String])
    val encodingOpt = checkEncoding(option.get(ENCODING).asInstanceOf[String])

    val maxErrorReport = checkMaxErrorReport(option.get(MAX_ERROR).asInstanceOf[String])
    for(
      encoding <- checkEncoding(option.get(ENCODING).asInstanceOf[String]);
      maxErrorReport <- checkMaxErrorReport(option.get(MAX_ERROR).asInstanceOf[String])
    ) yield (new CompilerConfig(classpath, "", encoding, ".", maxErrorReport))
  }

  private def compile(config: CompilerConfig, fileNames: Array[String]): Array[CompiledClass] = {
    new OnionCompiler(config).compile(fileNames)
  }

  private def checkClasspath(classpath: String): Array[String] = {
    if (classpath == null) return DEFAULT_CLASSPATH
    val paths: Array[String] = pathArray(classpath)
    paths
  }

  private def checkEncoding(encoding: String): Option[String] = {
    if (encoding == null) return Some(System.getProperty("file.encoding"))
    try {
      "".getBytes(encoding)
      Some(encoding)
    } catch {
      case e: UnsupportedEncodingException =>
        err.println(Messages.apply("error.command.invalidEncoding", ENCODING))
        None
    }
  }

  private def checkMaxErrorReport(maxErrorReport: String): Option[Int] = {
    if (maxErrorReport == null) return Some(DEFAULT_MAX_ERROR)
    val value: Option[Int] = try {
      Some(Integer.parseInt(maxErrorReport))
    } catch {
      case e: NumberFormatException => None
    }
    value match {
      case Some(v) if v > 0 => Some(v)
      case None =>
        err.println(Messages("error.command.requireNaturalNumber", MAX_ERROR))
        None
    }
  }
}
