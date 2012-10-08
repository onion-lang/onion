/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools

import java.lang.{Integer => JInteger}
import java.io.UnsupportedEncodingException
import java.util.Map
import onion.compiler._
import onion.compiler.exceptions.ScriptException
import onion.compiler.toolbox.Messages
import onion.compiler.toolbox.Systems
import onion.tools.option.CommandLineParser
import onion.tools.option.OptionConfig
import onion.tools.option.ParseFailure
import onion.tools.option.ParseResult
import onion.tools.option.ParseSuccess

/**
 *
 * @author Kota Mizushima
 *
 */
object ScriptRunner {
  private def conf(optionName: String, requireArgument: Boolean): OptionConfig = {
    new OptionConfig(optionName, requireArgument)
  }

  private def pathArray(path: String): Array[String] =  path.split(Systems.pathSeparator)

  private def printerr(message: String) {
    System.err.println(message)
  }

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
  private val commandLineParser = new CommandLineParser(Array[OptionConfig](conf(CLASSPATH, true), conf(SCRIPT_SUPER_CLASS, true), conf(ENCODING, true), conf(MAX_ERROR, true)))

  def run(commandLine: Array[String]): Int = {
    if (commandLine.length == 0) {
      printUsage
      return -1
    }
    val result: ParseSuccess = parseCommandLine(commandLine)
    if (result == null) return -1
    val config: CompilerConfig = createConfig(result)
    if (config == null) return -1
    val params: Array[String] = result.arguments.toArray(new Array[String](0)).asInstanceOf[Array[String]]
    if (params.length == 0) {
      printUsage
      return -1
    }
    val classes: Array[CompiledClass] = compile(config, Array[String](params(0)))
    if (classes == null) return -1
    val scriptParams: Array[String] = new Array[String](params.length - 1)
    var i: Int = 1
    while (i < params.length) {
      scriptParams(i - 1) = params(i)
      i += 1
    }
    val shell: OnionShell = new OnionShell(classOf[OnionClassLoader].getClassLoader, config.classPath)
    shell.run(classes, scriptParams)
  }

  protected def printUsage {
    printerr("Usage: onion [-options] <source file> <command line arguments>")
    printerr("options: ")
    printerr("  -super <super class>        specify script's super class")
    printerr("  -classpath <class path>     specify classpath")
    printerr("  -encoding <encoding>        specify source file encoding")
    printerr("  -maxErrorReport <number>    set number of errors reported")
  }

  private def parseCommandLine(commandLine: Array[String]): ParseSuccess = {
    val result: ParseResult = commandLineParser.parse(commandLine)
    if (result.status == ParseResult.FAILURE) {
      val failure: ParseFailure = result.asInstanceOf[ParseFailure]
      val lackedOptions: Array[String] = failure.lackedOptions
      val invalidOptions: Array[String] = failure.invalidOptions
      var i: Int = 0
      while (i < invalidOptions.length) {
        i += 1;
      }
      i = 0
      while (i < lackedOptions.length) {
        printerr(Messages.apply("error.command..noArgument", lackedOptions(i)))
        i += 1;
      }
      return null
    }
    result.asInstanceOf[ParseSuccess]
  }

  private def createConfig(result: ParseSuccess): CompilerConfig = {
    val option: Map[_, _] = result.options
    val noargOption: Map[_, _] = result.noArgumentOptions
    val classpath: Array[String] = checkClasspath(option.get(CLASSPATH).asInstanceOf[String])
    val encoding: String = checkEncoding(option.get(ENCODING).asInstanceOf[String])
    val maxErrorReport: JInteger = checkMaxErrorReport(option.get(MAX_ERROR).asInstanceOf[String])
    if (encoding == null || maxErrorReport == null) {
      return null
    }
    new CompilerConfig(classpath, "", encoding, ".", maxErrorReport.intValue)
  }

  private def compile(config: CompilerConfig, fileNames: Array[String]): Array[CompiledClass] = {
    new OnionCompiler(config).compile(fileNames)
  }

  private def checkClasspath(classpath: String): Array[String] = {
    if (classpath == null) return DEFAULT_CLASSPATH
    val paths: Array[String] = pathArray(classpath)
    paths
  }

  private def checkEncoding(encoding: String): String = {
    if (encoding == null) return System.getProperty("file.encoding")
    try {
      "".getBytes(encoding)
      encoding
    }
    catch {
      case e: UnsupportedEncodingException => {
        System.err.println(Messages.apply("error.command.invalidEncoding", ENCODING))
        null
      }
    }
  }

  private def checkMaxErrorReport(maxErrorReport: String): JInteger = {
    if (maxErrorReport == null) return JInteger.valueOf(DEFAULT_MAX_ERROR)
    val value: Option[Int] = (try {
      Some(Integer.parseInt(maxErrorReport))
    } catch {
      case e: NumberFormatException => None
    })
    value match {
      case Some(v) if v > 0 => JInteger.valueOf(v)
      case None =>
        printerr(Messages.apply("error.command.requireNaturalNumber", MAX_ERROR))
        null
    }
  }
}