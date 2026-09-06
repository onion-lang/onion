package onion.tools

import java.io.UnsupportedEncodingException
import onion.compiler.{CompilerConfig, OnionCompiler, WarningCategory, WarningLevel}
import onion.compiler.diagnostics.DiagnosticRenderer
import onion.compiler.pipeline.{CompilationResult, CompileProfileFormat, CompileProfileReporter, CompileProfileSettings}
import onion.compiler.toolbox.{Message, Systems}
import onion.compiler.verification.ArgGenerator
import onion.tools.option._

/**
 * The command-line options `onionc` and `onion` share: their names, how each is validated,
 * and how a parsed command line becomes a [[CompilerConfig]]. The two front ends differ only
 * in what they do with the result (write class files / run the script) and in a few options
 * of their own (`-d` and `-g:none` for `onionc`, `--watch` and `--stacktrace` for `onion`).
 */
object CompilerOptions {
  final val CLASSPATH: String = "-classpath"
  final val SCRIPT_SUPER_CLASS: String = "-super"
  final val ENCODING: String = "-encoding"
  final val OUTPUT: String = "-d"
  final val MAX_ERROR: String = "-maxErrorReport"
  final val VERBOSE: String = "--verbose"
  final val DUMP_AST: String = "--dump-ast"
  final val DUMP_TYPED_AST: String = "--dump-typed-ast"
  final val PROFILE_COMPILE: String = "--profile-compile"
  final val PROFILE_FORMAT: String = "--profile-format"
  final val PROFILE_OUTPUT: String = "--profile-output"
  final val WARN_LEVEL: String = "--warn"
  final val SUPPRESS_WARNINGS: String = "--Wno"
  final val NO_CHECK_LAWS: String = "--no-check-laws"
  final val LAW_SEED: String = "--law-seed"
  final val LAW_SAMPLES: String = "--law-samples"
  final val SHOW_EFFECTS: String = "--effects"
  /** Named after javac's -g:none, and meaning the same thing: no LocalVariableTable. */
  final val NO_DEBUG_INFO: String = "-g:none"
  final val STACKTRACE: String = "--stacktrace"

  final val DEFAULT_CLASSPATH: Array[String] = Array[String](".")
  final val DEFAULT_ENCODING: String = System.getProperty("file.encoding")
  final val DEFAULT_OUTPUT: String = "."
  final val DEFAULT_MAX_ERROR: Int = 10

  /** The option configurations both front ends accept, in `onionc`'s order. */
  def sharedOptionConfigs: Seq[OptionConfig] = Seq(
    OptionConfig(CLASSPATH, true),
    OptionConfig(SCRIPT_SUPER_CLASS, true),
    OptionConfig(ENCODING, true),
    OptionConfig(MAX_ERROR, true),
    OptionConfig(DUMP_AST, false),
    OptionConfig(DUMP_TYPED_AST, false),
    OptionConfig(PROFILE_COMPILE, false),
    OptionConfig(PROFILE_FORMAT, true),
    OptionConfig(PROFILE_OUTPUT, true),
    OptionConfig(WARN_LEVEL, true),
    OptionConfig(SUPPRESS_WARNINGS, true),
    OptionConfig(NO_CHECK_LAWS, false),
    OptionConfig(LAW_SEED, true),
    OptionConfig(LAW_SAMPLES, true),
    OptionConfig(SHOW_EFFECTS, false)
  )

  /** The shared options that take a value (their value is skipped when locating a script file). */
  final val VALUE_OPTIONS: Set[String] = Set(
    CLASSPATH, SCRIPT_SUPER_CLASS, ENCODING, MAX_ERROR,
    PROFILE_FORMAT, PROFILE_OUTPUT, WARN_LEVEL, SUPPRESS_WARNINGS,
    LAW_SEED, LAW_SAMPLES
  )

  private def printError(message: String): Unit = System.err.println(message)

  def pathArray(path: String): Array[String] = path.split(Systems.pathSeparator)

  /** Reports the invalid and value-less options of a failed parse, one line each. */
  def printFailure(failure: ParseFailure): Unit = {
    failure.invalidOptions.foreach { opt => printError(Message("error.command.invalidArgument", opt.value)) }
    failure.lackedOptions.foreach { opt => printError(Message("error.command.noArgument", opt.value)) }
  }

  /**
   * The [[CompilerConfig]] for a parsed command line, or None after reporting the first invalid
   * option. `outputDirectory` and `emitDebugInfo` are the two settings only `onionc` exposes.
   */
  def configFrom(
    option: Map[String, CommandLineParam],
    outputDirectory: String,
    verbose: Boolean,
    emitDebugInfo: Boolean = true
  ): Option[CompilerConfig] = {
    val classpath = checkClasspath(option.get(CLASSPATH))
    val dumpAst = option.get(DUMP_AST).contains(NoValuedParam)
    val dumpTypedAst = option.get(DUMP_TYPED_AST).contains(NoValuedParam)
    val checkLaws = !option.get(NO_CHECK_LAWS).contains(NoValuedParam)
    for {
      encoding <- checkEncoding(option.get(ENCODING))
      maxErrorReport <- checkMaxErrorReport(option.get(MAX_ERROR))
      profile <- parseCompileProfile(option)
      level <- parseWarningLevel(option.get(WARN_LEVEL))
      suppressed <- parseSuppressedWarnings(option.get(SUPPRESS_WARNINGS))
    } yield new CompilerConfig(
      classpath.toIndexedSeq,
      "",
      encoding,
      outputDirectory,
      maxErrorReport,
      verbose = verbose,
      warningLevel = level,
      suppressedWarnings = suppressed,
      dumpAst = dumpAst,
      dumpTypedAst = dumpTypedAst,
      compileProfile = profile,
      checkLaws = checkLaws,
      emitDebugInfo = emitDebugInfo,
      lawSeed = longParam(option, LAW_SEED, ArgGenerator.DefaultSeed),
      lawSamples = intParam(option, LAW_SAMPLES, ArgGenerator.DefaultSamples)
    )
  }

  /** A positive integer option, falling back to `default` when absent or unparsable. */
  def intParam(option: Map[String, CommandLineParam], name: String, default: Int): Int =
    option.get(name).collect { case ValuedParam(v) => v }
      .flatMap(v => v.toIntOption).filter(_ > 0).getOrElse(default)

  /** A long option, falling back to `default` when absent or unparsable. */
  def longParam(option: Map[String, CommandLineParam], name: String, default: Long): Long =
    option.get(name).collect { case ValuedParam(v) => v }
      .flatMap(v => v.toLongOption).getOrElse(default)

  def parseCompileProfile(option: Map[String, CommandLineParam]): Option[CompileProfileSettings] = {
    val enabled = option.get(PROFILE_COMPILE).contains(NoValuedParam)
    val format = option.get(PROFILE_FORMAT) match {
      case None | Some(NoValuedParam) => Some(CompileProfileFormat.Text)
      case Some(ValuedParam(value)) =>
        value.toLowerCase match {
          case "text" => Some(CompileProfileFormat.Text)
          case "json" => Some(CompileProfileFormat.Json)
          case _ =>
            printError(s"Invalid profile format: $value")
            None
        }
    }
    format.map { selected =>
      CompileProfileSettings(
        enabled = enabled,
        format = selected,
        output = option.get(PROFILE_OUTPUT).collect { case ValuedParam(value) => value }
      )
    }
  }

  def compile(config: CompilerConfig, fileNames: Array[String]): CompilationResult =
    new OnionCompiler(config).compileDetailed(fileNames)

  def emitDiagnostics(result: CompilationResult): Unit =
    DiagnosticRenderer.printDiagnostics(result.diagnostics)

  /** `--effects`: the inferred effect set of every compiled method, to stderr. */
  def emitEffects(result: CompilationResult): Unit = {
    val classes = result.debugArtifacts.typedClasses.getOrElse(Seq.empty)
    onion.compiler.effects.EffectInference.infer(classes).foreach { me => System.err.println(me.render) }
  }

  /** `--dump-ast`: the parsed AST, to stderr. Available even if a later phase fails. */
  def emitAstDump(result: CompilationResult): Unit =
    result.debugArtifacts.parsedUnits.foreach(DiagnosticRenderer.dumpAst(_))

  /** `--dump-typed-ast`: the typed AST summary, to stderr. */
  def emitTypedAstDump(result: CompilationResult): Unit =
    result.debugArtifacts.typedClasses.foreach(DiagnosticRenderer.dumpTyped(_))

  def emitProfile(config: CompilerConfig, result: CompilationResult): Unit = {
    if (config.verbose) System.err.println(CompileProfileReporter.renderVerbose(result.toCompileProfile))
    if (config.compileProfile.enabled) CompileProfileReporter.report(result.toCompileProfile, config.compileProfile)
  }

  def checkClasspath(param: Option[CommandLineParam]): Array[String] = param match {
    case Some(ValuedParam(classpath)) => pathArray(classpath)
    case Some(NoValuedParam) | None => DEFAULT_CLASSPATH
  }

  def checkEncoding(param: Option[CommandLineParam]): Option[String] = param match {
    case None | Some(NoValuedParam) => Some(DEFAULT_ENCODING)
    case Some(ValuedParam(encoding)) =>
      try { "".getBytes(encoding); Some(encoding) }
      catch {
        case _: UnsupportedEncodingException =>
          printError(Message("error.command.invalidEncoding", ENCODING))
          None
      }
  }

  def checkMaxErrorReport(param: Option[CommandLineParam]): Option[Int] = param match {
    case None | Some(NoValuedParam) => Some(DEFAULT_MAX_ERROR)
    case Some(ValuedParam(text)) =>
      text.toIntOption.filter(_ > 0).orElse {
        printError(Message("error.command.requireNaturalNumber", MAX_ERROR))
        None
      }
  }

  def parseWarningLevel(param: Option[CommandLineParam]): Option[WarningLevel] = param match {
    case Some(ValuedParam(value)) =>
      value.toLowerCase match {
        case "off" => Some(WarningLevel.Off)
        case "on" => Some(WarningLevel.On)
        case "error" => Some(WarningLevel.Error)
        case _ =>
          printError(Message("error.command.invalidArgument", WARN_LEVEL))
          None
      }
    case Some(NoValuedParam) =>
      printError(Message("error.command.noArgument", WARN_LEVEL))
      None
    case None => Some(WarningLevel.On)
  }

  def parseSuppressedWarnings(param: Option[CommandLineParam]): Option[Set[WarningCategory]] = param match {
    case Some(ValuedParam(value)) =>
      val tokens = value.split(",").map(_.trim).filter(_.nonEmpty)
      val parsed = tokens.flatMap(t => WarningCategory.fromString(t))
      if (parsed.length != tokens.length) {
        printError(Message("error.command.invalidArgument", SUPPRESS_WARNINGS))
        None
      } else Some(parsed.toSet)
    case Some(NoValuedParam) =>
      printError(Message("error.command.noArgument", SUPPRESS_WARNINGS))
      None
    case None => Some(Set.empty)
  }
}
