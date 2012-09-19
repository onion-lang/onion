/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools

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
    return new OptionConfig(optionName, requireArgument)
  }

  private def pathArray(path: String): Array[String] = {
    return path.split(Systems.pathSeparator)
  }

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
    val params: Array[String] = result.getArguments.toArray(new Array[String](0)).asInstanceOf[Array[String]]
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
    val shell: OnionShell = new OnionShell(classOf[OnionClassLoader].getClassLoader, config.getClassPath)
    return shell.run(classes, scriptParams)
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
    if (result.getStatus == ParseResult.FAILURE) {
      val failure: ParseFailure = result.asInstanceOf[ParseFailure]
      val lackedOptions: Array[String] = failure.getLackedOptions
      val invalidOptions: Array[String] = failure.getInvalidOptions
      var i: Int = 0
      while (i < invalidOptions.length) {
        i += 1;
      }
      i = 0
      while (i < lackedOptions.length) {
        printerr(Messages.get("error.command..noArgument", lackedOptions(i)))
        i += 1;
      }
      return null
    }
    return result.asInstanceOf[ParseSuccess]
  }

  private def createConfig(result: ParseSuccess): CompilerConfig = {
    val option: Map[_, _] = result.getOptions
    val noargOption: Map[_, _] = result.getNoArgumentOptions
    val classpath: Array[String] = checkClasspath(option.get(CLASSPATH).asInstanceOf[String])
    val encoding: String = checkEncoding(option.get(ENCODING).asInstanceOf[String])
    val maxErrorReport: Integer = checkMaxErrorReport(option.get(MAX_ERROR).asInstanceOf[String])
    if (encoding == null || maxErrorReport == null) {
      return null
    }
    return new CompilerConfig(classpath, "", encoding, ".", maxErrorReport.intValue)
  }

  private def compile(config: CompilerConfig, fileNames: Array[String]): Array[CompiledClass] = {
    return new OnionCompiler(config).compile(fileNames)
  }

  private def checkClasspath(classpath: String): Array[String] = {
    if (classpath == null) return DEFAULT_CLASSPATH
    val paths: Array[String] = pathArray(classpath)
    return paths
  }

  private def checkEncoding(encoding: String): String = {
    if (encoding == null) return System.getProperty("file.encoding")
    try {
      "".getBytes(encoding)
      return encoding
    }
    catch {
      case e: UnsupportedEncodingException => {
        System.err.println(Messages.get("error.command.invalidEncoding", ENCODING))
        return null
      }
    }
  }

  private def checkMaxErrorReport(maxErrorReport: String): Integer = {
    if (maxErrorReport == null) return new Integer(DEFAULT_MAX_ERROR)
    var value: Int = 0
    try {
      value = Integer.parseInt(maxErrorReport)
      if (value > 0) {
        return new Integer(value)
      }
    }
    catch {
      case e: NumberFormatException => {
      }
    }
    printerr(Messages.get("error.command.requireNaturalNumber", MAX_ERROR))
    return null
  }

}