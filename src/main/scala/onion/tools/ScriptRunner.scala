/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools

import java.io.UnsupportedEncodingException
import java.lang.System.err
import onion.compiler._
import onion.compiler.diagnostics.DiagnosticRenderer
import onion.compiler.exceptions.ScriptException
import onion.compiler.pipeline.{CompilationResult, CompileProfileFormat, CompileProfileReporter, CompileProfileSettings}
import onion.compiler.toolbox.Message
import onion.compiler.toolbox.Systems
import onion.tools.option._

/**
 *
 * @author Kota Mizushima
 *
 */
object ScriptRunner {
  val VERSION = "1.0.0"

  private def conf(optionName: String, requireArgument: Boolean) = OptionConfig(optionName, requireArgument)

  private def pathArray(path: String): Array[String] =  path.split(Systems.pathSeparator)

  def main(args: Array[String]): Unit = {
    // Handle help and version flags early
    if (args.exists(a => a == "-h" || a == "--help")) {
      new ScriptRunner().printUsage()
      return
    }
    if (args.exists(a => a == "-v" || a == "--version")) {
      println(s"Onion Script Runner version $VERSION")
      return
    }
    val verbose = args.exists(_ == "--verbose")
    val filteredArgs = args.filterNot(_ == "--verbose")
    try {
      new ScriptRunner().run(filteredArgs, verbose)
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
  private final val DUMP_AST: String = "--dump-ast"
  private final val DUMP_TYPED_AST: String = "--dump-typed-ast"
  private final val PROFILE_COMPILE: String = "--profile-compile"
  private final val PROFILE_FORMAT: String = "--profile-format"
  private final val PROFILE_OUTPUT: String = "--profile-output"
  private final val WARN_LEVEL: String = "--warn"
  private final val SUPPRESS_WARNINGS: String = "--Wno"
  private final val DEFAULT_CLASSPATH: Array[String] = Array[String](".")
  private final val DEFAULT_ENCODING: String = System.getProperty("file.encoding")
  private final val DEFAULT_MAX_ERROR: Int = 10
}

class ScriptRunner {
  import ScriptRunner._
  private[this] val parser = new CommandLineParser(
    conf(CLASSPATH, true),
    conf(SCRIPT_SUPER_CLASS, true),
    conf(ENCODING, true),
    conf(MAX_ERROR, true),
    conf(DUMP_AST, false),
    conf(DUMP_TYPED_AST, false),
    conf(PROFILE_COMPILE, false),
    conf(PROFILE_FORMAT, true),
    conf(PROFILE_OUTPUT, true),
    conf(WARN_LEVEL, true),
    conf(SUPPRESS_WARNINGS, true)
  )

  def run(commandLine: Array[String], verbose: Boolean = false): Int = {
    if (commandLine.isEmpty) {
      printUsage()
      return -1
    }
    parser.parse(commandLine) match {
      case failure@ParseFailure(_, _) =>
        printFailure(failure)
        return -1
      case success@ParseSuccess(_, _) =>
        val params = success.arguments
        if (params.isEmpty) {
          printUsage()
          return -1
        }
        createConfig(success, verbose) match {
          case None => -1
          case Some(config) =>
            val scriptArgs = params.drop(1).toArray
            val result = compile(config, Array(params.head))
            emitDiagnostics(result)
            emitProfile(config, result)
            if (result.hasErrors) {
              -1
            } else {
              new Shell(classOf[OnionClassLoader].getClassLoader, config.classPath).run(result.classes, scriptArgs) match {
                case Shell.Success(_) => 0
                case Shell.Failure(code) => code
              }
            }
        }
    }
  }

  protected def printUsage(): Unit = {
    println(
      s"""Onion Script Runner version ${ScriptRunner.VERSION}
         |
         |Usage: onion [options] <source_file> [arguments...]
         |
         |Options:
         |  -classpath <path>           Specify classpath
         |  -encoding <encoding>        Specify source file encoding
         |  -maxErrorReport <number>    Set maximum number of errors to report
         |  -super <super class>        Specify script's super class
         |  --verbose                   Show compilation phase timing
         |  --dump-ast                  Print parsed AST to stderr
         |  --dump-typed-ast            Print typed AST summary to stderr
         |  --profile-compile           Emit compile profile for all phases
         |  --profile-format <text|json>
         |                              Set profile output format (default: text)
         |  --profile-output <target>   Send profile to stderr, stdout, or a file path
         |  --warn <off|on|error>       Set warning level
         |  --Wno <codes>               Suppress warnings (e.g., W0001,unused-parameter)
         |  -h, --help                  Show this help message
         |  -v, --version               Show version information
         |
         |Examples:
         |  onion Hello.on
         |  onion -classpath lib/*.jar Script.on arg1 arg2""".stripMargin)
  }

  private def printFailure(failure: ParseFailure): Unit = {
    failure.lackedOptions.zipWithIndex.foreach{ case (lackedOption, i) =>
      err.println(Message("error.command.noArgument", lackedOption))
    }
  }

  private def createConfig(result: ParseSuccess, verbose: Boolean = false): Option[CompilerConfig] = {
    val option: Map[String, CommandLineParam] = result.options.toMap
    val classpath: Array[String] = checkClasspath(option.get(CLASSPATH))
    val dumpAst = option.get(DUMP_AST).contains(NoValuedParam)
    val dumpTypedAst = option.get(DUMP_TYPED_AST).contains(NoValuedParam)
    val compileProfile = parseCompileProfile(option)
    val warningLevel = parseWarningLevel(option.get(WARN_LEVEL))
    val suppressedWarnings = parseSuppressedWarnings(option.get(SUPPRESS_WARNINGS))
    for(
      encoding <- checkEncoding(option.get(ENCODING));
      maxErrorReport <- checkMaxErrorReport(option.get(MAX_ERROR));
      profile <- compileProfile;
      level <- warningLevel;
      suppressed <- suppressedWarnings
    ) yield (new CompilerConfig(
      classpath.toIndexedSeq,
      "",
      encoding,
      ".",
      maxErrorReport,
      verbose = verbose,
      warningLevel = level,
      suppressedWarnings = suppressed,
      dumpAst = dumpAst,
      dumpTypedAst = dumpTypedAst,
      compileProfile = profile
    ))
  }

  private def parseCompileProfile(option: Map[String, CommandLineParam]): Option[CompileProfileSettings] = {
    val enabled = option.get(PROFILE_COMPILE).contains(NoValuedParam)
    val format = option.get(PROFILE_FORMAT) match {
      case None => Some(CompileProfileFormat.Text)
      case Some(ValuedParam(value)) =>
        value.toLowerCase match {
          case "text" => Some(CompileProfileFormat.Text)
          case "json" => Some(CompileProfileFormat.Json)
          case _ =>
            err.println(s"Invalid profile format: $value")
            None
        }
      case Some(NoValuedParam) =>
        Some(CompileProfileFormat.Text)
    }
    format.map { selected =>
      CompileProfileSettings(
        enabled = enabled,
        format = selected,
        output = option.get(PROFILE_OUTPUT).collect { case ValuedParam(value) => value }
      )
    }
  }

  private def compile(config: CompilerConfig, fileNames: Array[String]): CompilationResult =
    new OnionCompiler(config).compileDetailed(fileNames)

  private def emitDiagnostics(result: CompilationResult): Unit =
    DiagnosticRenderer.printDiagnostics(result.diagnostics)

  private def emitProfile(config: CompilerConfig, result: CompilationResult): Unit = {
    if (config.verbose) {
      System.err.println(CompileProfileReporter.renderVerbose(result.toCompileProfile))
    }
    if (config.compileProfile.enabled) {
      CompileProfileReporter.report(result.toCompileProfile, config.compileProfile)
    }
  }

  private def checkClasspath(optClasspath: Option[CommandLineParam]): Array[String] = {
    optClasspath match {
      case Some(ValuedParam(classpath)) => pathArray(classpath)
      case Some(NoValuedParam) | None => DEFAULT_CLASSPATH
    }
  }

  private def checkEncoding(optEncoding: Option[CommandLineParam]): Option[String] = {
    optEncoding match {
      case None | Some(NoValuedParam) => 
        Some(System.getProperty("file.encoding"))
      case Some(ValuedParam(encoding)) => 
        try {
          "".getBytes(encoding)
          Some(encoding)
        } catch {
          case e: UnsupportedEncodingException =>
            err.println(Message.apply("error.command.invalidEncoding", ENCODING))
            None
        }
    }
  }

  private def checkMaxErrorReport(optMaxErrorReport: Option[CommandLineParam]): Option[Int] = {
    optMaxErrorReport match {
      case None | Some(NoValuedParam) => Some(DEFAULT_MAX_ERROR)
      case Some(ValuedParam(maxErrorReport)) => 
        val value: Option[Int] = try {
          Some(Integer.parseInt(maxErrorReport))
        } catch {
          case e: NumberFormatException => None
        }
        value match {
          case Some(v) if v > 0 => Some(v)
          case Some(_) | None =>
            err.println(Message("error.command.requireNaturalNumber", MAX_ERROR))
            None
        }
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
            err.println(Message.apply("error.command.invalidArgument", WARN_LEVEL))
            None
        }
      case Some(NoValuedParam) =>
        err.println(Message.apply("error.command.noArgument", WARN_LEVEL))
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
          err.println(Message.apply("error.command.invalidArgument", SUPPRESS_WARNINGS))
          None
        } else {
          Some(parsed.toSet)
        }
      case Some(NoValuedParam) =>
        err.println(Message.apply("error.command.noArgument", SUPPRESS_WARNINGS))
        None
      case None =>
        Some(Set.empty)
    }
  }
}
