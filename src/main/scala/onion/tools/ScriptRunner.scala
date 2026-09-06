/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools

import onion.compiler._
import onion.compiler.exceptions.ScriptException
import onion.tools.option._
import onion.tools.CompilerOptions.*

/**
 *
 * @author Kota Mizushima
 *
 */
object ScriptRunner {
  /** What `execute` needs after a successful compile: the script's name, the class path and classes, and the script's own arguments. */
  final case class Prepared(scriptName: String, classPath: Seq[String], classes: Seq[CompiledClass], scriptArgs: Array[String])

  val VERSION = OnionVersion.value

  /**
   * Index of the script-file argument: walks the leading runner options
   * (skipping each value-taking option's value). Everything from that index
   * on belongs to the script, so script arguments like --count are never
   * mistaken for runner options (python/node behavior).
   */
  private[tools] def scriptIndex(args: Array[String]): Int = {
    var i = 0
    while (i < args.length && args(i).startsWith("-")) {
      i += (if (VALUE_OPTIONS.contains(args(i))) 2 else 1)
    }
    i
  }

  def main(args: Array[String]): Unit = {
    val exitCode = runMain(args)
    if (exitCode != 0) System.exit(exitCode)
  }

  def runMain(args: Array[String]): Int = {
    // Only the runner-option prefix (before the script file) is inspected;
    // flags after the script path are the script's own arguments.
    val prefix = args.take(scriptIndex(args))
    if (prefix.exists(a => a == "-h" || a == "--help")) {
      new ScriptRunner().printUsage()
      return 0
    }
    if (prefix.exists(a => a == "-v" || a == "--version")) {
      println(s"Onion Script Runner version $VERSION")
      return 0
    }
    val verbose = prefix.exists(_ == "--verbose")
    val watch = prefix.exists(_ == "--watch")
    val si = scriptIndex(args)
    val filteredArgs = args.take(si).filterNot(a => a == "--verbose" || a == "--watch") ++ args.drop(si)
    if (watch) {
      runWatching(filteredArgs, verbose)
      return 0
    }
    // An uncaught runtime error used to be rethrown so the JVM's default handler
    // printed it, which meant synthesized wrappers and launcher frames the user never
    // wrote (issue #450). Render it like a diagnostic instead; --stacktrace keeps the
    // untouched trace for when the JVM detail is the point.
    val wantTrace = prefix.exists(_ == STACKTRACE)
    val runnerArgs = filteredArgs.filterNot(_ == STACKTRACE)
    try {
      // With ONION_DAEMON set, the compile happens in the resident daemon and only the run
      // happens here (the program must run in the user's own process). When the daemon
      // cannot be reached, everything happens here as before.
      val viaDaemon =
        if (onion.tools.daemon.DaemonClient.enabledByEnvironment) onion.tools.daemon.DaemonClient.compileScript(runnerArgs) else None
      viaDaemon match {
        case Some(Left(exitCode)) => exitCode
        case Some(Right(prepared)) => new ScriptRunner().execute(prepared)
        case None => new ScriptRunner().run(runnerArgs, verbose)
      }
    }
    catch {
      case e: ScriptException =>
        val cause = e.getCause
        if (wantTrace) throw cause
        val script = args.lift(scriptIndex(args)).getOrElse("<script>")
        System.err.print(RuntimeErrorReporter.render(cause, script))
        1
    }
  }

  /**
   * --watch: run the script, then rerun it whenever the file changes.
   * Runtime exceptions and compile errors are printed but don't stop
   * the watch loop.
   */
  private def runWatching(args: Array[String], verbose: Boolean): Unit = {
    import java.nio.file.{FileSystems, Paths, StandardWatchEventKinds}
    val scriptPathOption = args.find(a => !a.startsWith("-"))
    if (scriptPathOption.isEmpty) {
      new ScriptRunner().printUsage()
      return
    }
    val scriptPath = scriptPathOption.get
    val path = Paths.get(scriptPath).toAbsolutePath
    val dir = path.getParent
    val watcher = FileSystems.getDefault.newWatchService()
    dir.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE)

    def runOnce(): Unit = {
      try {
        new ScriptRunner().run(args, verbose)
      } catch {
        case e: ScriptException =>
          Option(e.getCause).getOrElse(e).printStackTrace()
        case e: Exception =>
          e.printStackTrace()
      }
      System.err.println(s"[watch] ${path.getFileName} — waiting for changes (Ctrl-C to stop)")
    }

    runOnce()
    while (true) {
      val key = watcher.take()
      import scala.jdk.CollectionConverters.*
      val touched = key.pollEvents().asScala.exists { event =>
        event.context() match {
          case p: java.nio.file.Path => dir.resolve(p) == path
          case _ => false
        }
      }
      key.reset()
      if (touched) {
        Thread.sleep(80) // editors often write in bursts
        var drained = watcher.poll()
        while (drained != null) {
          drained.pollEvents()
          drained.reset()
          drained = watcher.poll()
        }
        runOnce()
      }
    }
  }

}

class ScriptRunner {
  private[this] val parser = new CommandLineParser(sharedOptionConfigs*)

  def run(commandLine: Array[String], verbose: Boolean = false): Int =
    prepare(commandLine, verbose) match {
      case Left(exitCode) => exitCode
      case Right(prepared) => execute(prepared)
    }

  /**
   * The compile half of `run`: option parsing, compilation, diagnostics and profile output.
   * Returns the exit code when there is nothing to run, else what `execute` needs. Split out
   * so the compile daemon can do this half in its own process and hand the classes back.
   */
  def prepare(commandLine: Array[String], verbose: Boolean = false): Either[Int, ScriptRunner.Prepared] = {
    if (commandLine.isEmpty) {
      printUsage()
      return Left(-1)
    }
    // Runner options end at the script file; everything after it goes to the
    // script verbatim (so script flags like --count are never parsed here).
    val si = ScriptRunner.scriptIndex(commandLine)
    if (si >= commandLine.length) {
      printUsage()
      return Left(-1)
    }
    val runnerLine = commandLine.take(si + 1)
    val passThroughArgs = commandLine.drop(si + 1)
    parser.parse(runnerLine) match {
      case failure@ParseFailure(_, _) =>
        printFailure(failure)
        return Left(-1)
      case success@ParseSuccess(_, _) =>
        val params = success.arguments
        if (params.isEmpty) {
          printUsage()
          return Left(-1)
        }
        createConfig(success, verbose) match {
          case None => Left(-1)
          case Some(config) =>
            val scriptArgs = passThroughArgs
            val result = compile(config, Array(params.head))
            if (config.dumpAst) emitAstDump(result)
            if (config.dumpTypedAst) emitTypedAstDump(result)
            emitDiagnostics(result)
            emitProfile(config, result)
            if (!result.hasErrors && success.options.contains(SHOW_EFFECTS)) {
              val typed = result.debugArtifacts.typedClasses.getOrElse(Seq.empty)
              onion.compiler.effects.EffectInference.infer(typed).foreach { me =>
                System.err.println(me.render)
              }
            }
            if (result.hasErrors) Left(-1)
            else Right(ScriptRunner.Prepared(new java.io.File(params.head).getName, config.classPath, result.classes, scriptArgs))
        }
    }
  }

  /** The run half of `run`: executes compiled classes in this process. */
  def execute(prepared: ScriptRunner.Prepared): Int = {
    // Expose the script's file name so auto-CLI usage messages read
    // `usage: myscript.on <arg>` instead of the literal `<script>`.
    System.setProperty("onion.cli.script", prepared.scriptName)
    new Shell(classOf[OnionClassLoader].getClassLoader, prepared.classPath).run(prepared.classes, prepared.scriptArgs) match {
      // main's returned Int is the documented exit code (docs/guide/tools.md's
      // "Failures are exit codes ... return 1 from main"); a void main (or any
      // other return type) has no such meaning, so it keeps exiting 0.
      case Shell.Success(value: java.lang.Integer) => value.intValue()
      case Shell.Success(_) => 0
      case Shell.Failure(code) => code
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
         |  --no-check-laws             Do not execute record `law`/`example` clauses
         |  --law-seed <n>              RNG seed for law sample generation
         |  --law-samples <n>           Number of samples generated per law parameter
         |  --effects                   Print each method's inferred effect set to stderr
         |  --stacktrace                Print the raw JVM trace for an uncaught runtime error
         |  --watch                     Re-run the script whenever its file changes
         |  -h, --help                  Show this help message
         |  -v, --version               Show version information
         |
         |Examples:
         |  onion Hello.on
         |  onion -classpath lib/*.jar Script.on arg1 arg2""".stripMargin)
  }

  private def createConfig(result: ParseSuccess, verbose: Boolean): Option[CompilerConfig] =
    configFrom(result.options.toMap, ".", verbose)
}
