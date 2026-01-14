/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
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
import onion.compiler.{CompiledClass, CompilerConfig, OnionCompiler, WarningCategory, WarningLevel}
import onion.compiler.CompilationOutcome
import onion.compiler.CompilationOutcome.{Failure, Success}
import onion.compiler.CompilationReporter
import onion.compiler.exceptions.ScriptException
import onion.compiler.toolbox.Message
import onion.compiler.toolbox.Systems
import onion.tools.option._
import scala.jdk.CollectionConverters.*
import scala.util.boundary, boundary.break

/**
 *
 * @author Kota Mizushima
 *
 */
object CompilerFrontend {
  val VERSION = "1.0.0"

  private def config(option: String, requireArg: Boolean): OptionConfig = new OptionConfig(option, requireArg)

  private def pathArray(path: String): Array[String] = path.split(Systems.pathSeparator)

  private def printError(message: String): Unit = System.err.println(message)

  def main(args: Array[String]): Unit = {
    // Handle help and version flags early
    if (args.exists(a => a == "-h" || a == "--help")) {
      new CompilerFrontend().printUsage()
      return
    }
    if (args.exists(a => a == "-v" || a == "--version")) {
      println(s"Onion Compiler version $VERSION")
      return
    }
    val verbose = args.exists(_ == "--verbose")
    val filteredArgs = args.filterNot(_ == "--verbose")
    try {
      new CompilerFrontend().run(filteredArgs, verbose)
    } catch {
      case e: ScriptException => throw e.getCause
    }
  }

  private final val CLASSPATH: String = "-classpath"
  private final val SCRIPT_SUPER_CLASS: String = "-super"
  private final val ENCODING: String = "-encoding"
  private final val OUTPUT: String = "-d"
  private final val MAX_ERROR: String = "-maxErrorReport"
  private final val VERBOSE: String = "--verbose"
  private final val DUMP_AST: String = "--dump-ast"
  private final val DUMP_TYPED_AST: String = "--dump-typed-ast"
  private final val WARN_LEVEL: String = "--warn"
  private final val SUPPRESS_WARNINGS: String = "--Wno"
  private final val DEFAULT_CLASSPATH: Array[String] = Array[String](".")
  private final val DEFAULT_ENCODING: String = System.getProperty("file.encoding")
  private final val DEFAULT_OUTPUT: String = "."
  private final val DEFAULT_MAX_ERROR: Int = 10
}

class CompilerFrontend {

  import CompilerFrontend._

  private val commandLineParser = new CommandLineParser(
    config(CLASSPATH, true),
    config(SCRIPT_SUPER_CLASS, true),
    config(ENCODING, true),
    config(OUTPUT, true),
    config(MAX_ERROR, true),
    config(DUMP_AST, false),
    config(DUMP_TYPED_AST, false),
    config(WARN_LEVEL, true),
    config(SUPPRESS_WARNINGS, true)
  )

  def run(commandLine: Array[String], verbose: Boolean = false): Int = {
    if (commandLine.length == 0) {
      printUsage()
      return -1
    }
    val result: Option[ParseSuccess] = parseCommandLine(commandLine)
    result match {
      case None => -1
      case Some(success) =>
        val params: Array[String] = success.arguments.toArray
        if (params.length == 0) {
          printUsage()
          return -1
        }
        createConfig(success, verbose) match {
          case None => -1
          case Some(config) =>
            compile(config, params) match {
              case Success(classes) =>
                val generated = generateFiles(classes)
                if (generated) 0 else -1
              case Failure(errors) =>
                CompilationReporter.printErrors(errors)
                -1
            }
        }
    }
  }

  private def simpleNameOf(fqcn: String): String = {
    val index = fqcn.lastIndexOf(".")
    if (fqcn.lastIndexOf(".") < 0)  fqcn else fqcn.substring(index + 1, fqcn.length)
  }

  private def outputPathOf(outDir: String, fqcn: String): String = outDir + Systems.fileSeparator + simpleNameOf(fqcn)+ ".class"

  private def generateFiles(binaries: Seq[CompiledClass]): Boolean = boundary {
    val generated: java.util.List[File] = new java.util.ArrayList[File]
    for(binary <- binaries) {
      val outDir: String = binary.outputPath
      new File(outDir).mkdirs
      val outPath: String = outputPathOf(outDir, binary.className)
      val targetFile: File = new File(outPath)
      try {
        if (!targetFile.exists) targetFile.createNewFile
        generated.add(targetFile)
        using(new BufferedOutputStream(new FileOutputStream(targetFile))){out =>
          out.write(binary.content)
        }
      } catch {
        case e: IOException =>
          generated.asScala.foreach(_.delete)
          break(false)
      }
    }
    true
  }

  protected def printUsage(): Unit = {
    println(
      s"""Onion Compiler version ${CompilerFrontend.VERSION}
         |
         |Usage: onionc [options] source_file ...
         |
         |Options:
         |  -classpath <path>           Specify classpath
         |  -d <path>                   Specify output directory
         |  -encoding <encoding>        Specify source file encoding
         |  -maxErrorReport <number>    Set maximum number of errors to report
         |  -super <super class>        Specify script's super class
         |  --verbose                   Show compilation phase timing
         |  --dump-ast                  Print parsed AST to stderr
         |  --dump-typed-ast            Print typed AST summary to stderr
         |  --warn <off|on|error>       Set warning level
         |  --Wno <codes>               Suppress warnings (e.g., W0001,unused-parameter)
         |  -h, --help                  Show this help message
         |  -v, --version               Show version information
         |
         |Examples:
         |  onionc Hello.on
         |  onionc -d out -classpath lib/*.jar *.on""".stripMargin)
  }

  private def parseCommandLine(commandLine: Array[String]): Option[ParseSuccess] = {
    val result = commandLineParser.parse(commandLine)
    result match {
      case success: ParseSuccess => Some(success)
      case failure: ParseFailure =>
        val lackedOptions = failure.lackedOptions
        val invalidOptions = failure.invalidOptions
        invalidOptions.foreach{opt => printError(Message.apply("error.command.invalidArgument", opt)) }
        lackedOptions.foreach{opt => printError(Message.apply("error.command..noArgument", opt)) }
        None
    }
  }

  private def createConfig(result: ParseSuccess, verbose: Boolean = false): Option[CompilerConfig] = {
    val option: Map[String, CommandLineParam] = result.options.toMap
    val classpath: Array[String] = checkClasspath(
      option.get(CLASSPATH).collect{ case ValuedParam(value) => value }
    )
    val encoding: Option[String] = checkEncoding(
      option.get(ENCODING).collect{ case ValuedParam(value) => value }
    )
    val outputDirectory: String = checkOutputDirectory(
      option.get(OUTPUT).collect{ case ValuedParam(value) => value}
    )
    val maxErrorReport: Option[Int] = checkMaxErrorReport(
      option.get(MAX_ERROR).collect{ case ValuedParam(value) => value}
    )
    val dumpAst = option.get(DUMP_AST).contains(NoValuedParam)
    val dumpTypedAst = option.get(DUMP_TYPED_AST).contains(NoValuedParam)
    val warningLevel = parseWarningLevel(option.get(WARN_LEVEL))
    val suppressedWarnings = parseSuppressedWarnings(option.get(SUPPRESS_WARNINGS))
    for {
      e <- encoding
      m <- maxErrorReport
      level <- warningLevel
      suppressed <- suppressedWarnings
    } yield {
      new CompilerConfig(
        classpath.toIndexedSeq,
        "",
        e,
        outputDirectory,
        m,
        verbose = verbose,
        warningLevel = level,
        suppressedWarnings = suppressed,
        dumpAst = dumpAst,
        dumpTypedAst = dumpTypedAst
      )
    }
  }

  private def compile(config: CompilerConfig, fileNames: Array[String]): CompilationOutcome = {
    new OnionCompiler(config).compile(fileNames)
  }

  private def checkClasspath(classpath: Option[String]): Array[String] = {
    (for (c <- classpath) yield pathArray(c)).getOrElse(DEFAULT_CLASSPATH)
  }

  private def checkOutputDirectory(outputDirectory: Option[String]): String = outputDirectory.getOrElse(DEFAULT_OUTPUT)

  private def checkEncoding(encoding: Option[String]): Option[String] = {
    try {
      (for (e <- encoding) yield {
        "".getBytes(e)
        e
      }).orElse(Some(DEFAULT_ENCODING))
    } catch {
      case e: UnsupportedEncodingException => {
        printError(Message.apply("error.command.invalidEncoding", ENCODING))
        None
      }
    }
  }

  private def checkMaxErrorReport(maxErrorReport: Option[String]): Option[Int] = {
    try {
      maxErrorReport match {
        case Some(m) =>
          val value = Integer.parseInt(m)
          if (value > 0) Some(value) else None
        case None => Some(DEFAULT_MAX_ERROR)
      }
    } catch {
      case e: NumberFormatException =>
        printError(Message.apply("error.command.requireNaturalNumber", MAX_ERROR))
        None
    }
  }

  private def parseWarningLevel(param: Option[CommandLineParam]): Option[WarningLevel] = {
    param match {
      case Some(ValuedParam(value)) =>
        value.toLowerCase match {
          case "off" => Some(WarningLevel.Off)
          case "on" => Some(WarningLevel.On)
          case "error" => Some(WarningLevel.Error)
          case _ =>
            printError(Message.apply("error.command.invalidArgument", WARN_LEVEL))
            None
        }
      case Some(NoValuedParam) =>
        printError(Message.apply("error.command.noArgument", WARN_LEVEL))
        None
      case None =>
        Some(WarningLevel.On)
    }
  }

  private def parseSuppressedWarnings(param: Option[CommandLineParam]): Option[Set[WarningCategory]] = {
    param match {
      case Some(ValuedParam(value)) =>
        val tokens = value.split(",").map(_.trim).filter(_.nonEmpty)
        val parsed = tokens.flatMap(t => WarningCategory.fromString(t))
        if (parsed.length != tokens.length) {
          printError(Message.apply("error.command.invalidArgument", SUPPRESS_WARNINGS))
          None
        } else {
          Some(parsed.toSet)
        }
      case Some(NoValuedParam) =>
        printError(Message.apply("error.command.noArgument", SUPPRESS_WARNINGS))
        None
      case None =>
        Some(Set.empty)
    }
  }

}
