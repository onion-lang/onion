/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools

import onion.compiler.{CompiledClass, CompilerConfig}
import onion.compiler.exceptions.ScriptException
import onion.tools.option._
import onion.tools.CompilerOptions.*

/**
 *
 * @author Kota Mizushima
 *
 */
object CompilerFrontend {
  val VERSION = OnionVersion.value

  def main(args: Array[String]): Unit = {
    // With ONION_DAEMON set, the command line goes to the resident compile daemon (see
    // onion.tools.daemon.DaemonClient); when the daemon cannot be reached, compile here.
    val viaDaemon =
      if (onion.tools.daemon.DaemonClient.enabledByEnvironment && !args.exists(a => a == "-h" || a == "--help" || a == "-v" || a == "--version"))
        onion.tools.daemon.DaemonClient.compile(args)
      else None
    val exitCode = viaDaemon.getOrElse(runCommandLine(args))
    if (exitCode != 0) System.exit(exitCode)
  }

  /** `main` without the exit: runs a complete `onionc` command line and returns its exit code. */
  def runCommandLine(args: Array[String]): Int = {
    // Handle help and version flags early
    if (args.exists(a => a == "-h" || a == "--help")) {
      new CompilerFrontend().printUsage()
      return 0
    }
    if (args.exists(a => a == "-v" || a == "--version")) {
      println(s"Onion Compiler version $VERSION")
      return 0
    }
    val verbose = args.exists(_ == "--verbose")
    val filteredArgs = args.filterNot(_ == "--verbose")
    try new CompilerFrontend().run(filteredArgs, verbose)
    catch {
      case e: ScriptException => throw e.getCause
    }
  }

}

class CompilerFrontend {


  private val commandLineParser = new CommandLineParser(
    (sharedOptionConfigs :+ OptionConfig(OUTPUT, true) :+ OptionConfig(NO_DEBUG_INFO, false))*
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
            val result = compile(config, params)
            if (config.dumpAst) emitAstDump(result)
            if (config.dumpTypedAst) emitTypedAstDump(result)
            emitDiagnostics(result)
            emitProfile(config, result)
            if (!result.hasErrors && success.options.contains(SHOW_EFFECTS)) emitEffects(result)
            if (result.hasErrors) -1
            else if (generateFiles(result.classes)) 0
            else -1
        }
    }
  }

  private def generateFiles(binaries: Seq[CompiledClass]): Boolean =
    CompiledClassWriter.writeAll(binaries).isRight

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
         |  --profile-compile           Emit compile profile for all phases
         |  --profile-format <text|json>
         |                              Set profile output format (default: text)
         |  --profile-output <target>   Send profile to stderr, stdout, or a file path
         |  --warn <off|on|error>       Set warning level
         |  --Wno <codes>               Suppress warnings (e.g., W0001,unused-parameter)
         |  --no-check-laws             Do not execute record `law`/`example` clauses
         |  --law-seed <n>              RNG seed for law sample generation
         |  --law-samples <n>           Number of samples generated per law parameter
         |  --effects                   Print each method's inferred effect set to stderr
         |  -g:none                     Omit the LocalVariableTable (smaller class files,
         |                              but a debugger can no longer show variable values)
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
        printFailure(failure)
        None
    }
  }

  private def createConfig(result: ParseSuccess, verbose: Boolean): Option[CompilerConfig] = {
    val option: Map[String, CommandLineParam] = result.options.toMap
    val outputDirectory = option.get(OUTPUT).collect { case ValuedParam(value) => value }.getOrElse(DEFAULT_OUTPUT)
    configFrom(option, outputDirectory, verbose, emitDebugInfo = !option.get(NO_DEBUG_INFO).contains(NoValuedParam))
  }
}
