/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.util.ArrayList
import java.util.Iterator
import java.util.List
import java.util.Map
import onion.compiler.CompiledClass
import onion.compiler.OnionCompiler
import onion.compiler.CompilerConfig
import onion.compiler.exceptions.ScriptException
import onion.compiler.toolbox.Messages
import onion.compiler.toolbox.Systems
import onion.tools.option.CommandLineParser
import onion.tools.option.OptionConf
import onion.tools.option.ParseFailure
import onion.tools.option.ParseResult
import onion.tools.option.ParseSuccess

/**
 *
 * @author Kota Mizushima
 *
 */
object OnionCompilerFrontend {
  private def conf(option: String, requireArg: Boolean): OptionConf = new OptionConf(option, requireArg)

  private def pathArray(path: String): Array[String] = path.split(Systems.pathSeparator)

  private def printerr(message: String): Unit = System.err.println(message)

  def main(args: Array[String]): Unit = {
    try {
      new OnionCompilerFrontend().run(args)
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
  private final val OUTPUT: String = "-d"
  private final val MAX_ERROR: String = "-maxErrorReport"
  private final val DEFAULT_CLASSPATH: Array[String] = Array[String](".")
  private final val DEFAULT_ENCODING: String = System.getProperty("file.encoding")
  private final val DEFAULT_OUTPUT: String = "."
  private final val DEFAULT_MAX_ERROR: Int = 10
}

class OnionCompilerFrontend {

  import OnionCompilerFrontend._

  private val commandLineParser = new CommandLineParser(Array[OptionConf](conf(CLASSPATH, true), conf(SCRIPT_SUPER_CLASS, true), conf(ENCODING, true), conf(OUTPUT, true), conf(MAX_ERROR, true)))

  def run(commandLine: Array[String]): Int = {
    if (commandLine.length == 0) {
      printUsage
      return -1
    }
    val result: Option[ParseSuccess] = parseCommandLine(commandLine)
    result match {
      case None => -1
      case Some(success) =>
        val config: CompilerConfig = createConfig(success)
        val params: Array[String] = success.getArguments.toArray(new Array[String](0)).asInstanceOf[Array[String]]
        if (params.length == 0) {
          printUsage
          return -1
        }
        if (config == null) return -1
        val classes: Array[CompiledClass] = compile(config, params)
        if (classes == null) return -1
        if (generateFiles(classes)) 0 else -1
    }
  }

  private def simpleNameOf(fqcn: String): String = {
    val index = fqcn.lastIndexOf(".")
    if (fqcn.lastIndexOf(".") < 0)  fqcn else fqcn.substring(index + 1, fqcn.length)
  }

  private def outputPathOf(outDir: String, fqcn: String): String = outDir + Systems.fileSeparator + simpleNameOf(fqcn)+ ".class"

  private def generateFiles(binaries: Array[CompiledClass]): Boolean = {
    val generated: java.util.List[File] = new java.util.ArrayList[File]

    var i: Int = 0
    while (i < binaries.length) {
      val binary: CompiledClass = binaries(i)
      val outDir: String = binary.getOutputPath
      new File(outDir).mkdirs
      val outPath: String = outputPathOf(outDir, binary.getClassName)
      val targetFile: File = new File(outPath)
      try {
        if (!targetFile.exists) targetFile.createNewFile
        generated.add(targetFile)
        val out: BufferedOutputStream = new BufferedOutputStream(new FileOutputStream(targetFile))
        try {
          out.write(binary.getContent)
        } finally {
          out.close
        }
      } catch {
        case e: IOException =>
          val it: Iterator[_] = generated.iterator
          while (it.hasNext) {
            (it.next.asInstanceOf[File]).delete
          }
          return false
      }
      i += 1
    }

    true
  }

  protected def printUsage {
    printerr("Usage: onionc [-options] source_file ...")
    printerr("options: ")
    printerr("  -super <super class>        specify script's super class")
    printerr("  -d <path>                   specify output directory")
    printerr("  -classpath <path>           specify classpath")
    printerr("  -encoding <encoding>        specify source file encoding")
    printerr("  -maxErrorReport <number>    set number of errors reported")
  }

  private def parseCommandLine(commandLine: Array[String]): Option[ParseSuccess] = {
    val result: ParseResult = commandLineParser.parse(commandLine)
    result match {
      case success: ParseSuccess => Some(success)
      case failure: ParseFailure =>
        val failure = result.asInstanceOf[ParseFailure]
        val lackedOptions = failure.getLackedOptions
        val invalidOptions = failure.getInvalidOptions
        invalidOptions.foreach{opt => printerr(Messages.get("error.command.invalidArgument", opt)) }
        lackedOptions.foreach{opt => printerr(Messages.get("error.command..noArgument", opt)) }
        None
    }
  }

  private def createConfig(result: ParseSuccess): CompilerConfig = {
    val option: Map[_, _] = result.getOptions
    val noargOption: Map[_, _] = result.getNoArgumentOptions
    val classpath: Array[String] = checkClasspath(option.get(CLASSPATH).asInstanceOf[String])
    val encoding: String = checkEncoding(option.get(ENCODING).asInstanceOf[String])
    val outputDirectory: String = checkOutputDirectory(option.get(OUTPUT).asInstanceOf[String])
    val maxErrorReport: Integer = checkMaxErrorReport(option.get(MAX_ERROR).asInstanceOf[String])
    if (encoding == null || maxErrorReport == null || outputDirectory == null) {
      return null
    }
    return new CompilerConfig(classpath, "", encoding, outputDirectory, maxErrorReport.intValue)
  }

  private def compile(config: CompilerConfig, fileNames: Array[String]): Array[CompiledClass] = {
    val compiler: OnionCompiler = new OnionCompiler(config)
    return compiler.compile(fileNames)
  }

  private def checkClasspath(classpath: String): Array[String] = {
    if (classpath == null) return DEFAULT_CLASSPATH
    val paths: Array[String] = pathArray(classpath)
    return paths
  }

  private def checkOutputDirectory(outputDirectory: String): String = {
    if (outputDirectory == null) return DEFAULT_OUTPUT
    return outputDirectory
  }

  private def checkEncoding(encoding: String): String = {
    if (encoding == null) return DEFAULT_ENCODING
    try {
      "".getBytes(encoding)
      return encoding
    }
    catch {
      case e: UnsupportedEncodingException => {
        printerr(Messages.get("error.command.invalidEncoding", ENCODING))
        return null
      }
    }
  }

  private def checkMaxErrorReport(maxErrorReport: String): Integer = {
    if (maxErrorReport == null) return new Integer(DEFAULT_MAX_ERROR)
    try {
      val value: Int = Integer.parseInt(maxErrorReport)
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