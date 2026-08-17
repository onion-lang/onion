package onion.tools

import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths

import onion.tools.project.ProjectCommands

private[tools] trait LegacyCommands:
  def script(args: Array[String]): Int
  def repl(args: Array[String]): Int

object OnionCli:
  private val Usage =
    """Usage:
      |  onion new <name>
      |  onion build [--verbose]
      |  onion run [--verbose] [-- <arguments...>]
      |  onion test [--verbose] [--report-xml <path>]
      |  onion clean
      |  onion doc [-d <dir>] [<source.on>...]
      |  onion fmt [--check] [<path>...]
      |  onion repl [repl-options...]
      |  onion [script-runner-options...] <source.on> [arguments...]
      |""".stripMargin

  def main(args: Array[String]): Unit =
    val exitCode = run(args, Paths.get("").toAbsolutePath, System.out, System.err)
    if exitCode != 0 then System.exit(exitCode)

  def run(
    args: Array[String],
    cwd: Path,
    out: PrintStream,
    err: PrintStream
  ): Int =
    val legacy = new LegacyCommands:
      override def script(args: Array[String]): Int =
        ScriptRunner.runMain(args)

      override def repl(args: Array[String]): Int =
        Repl.main(args)
        0

    run(args, cwd, out, err, legacy, ProjectCommands())

  private[tools] def run(
    args: Array[String],
    cwd: Path,
    out: PrintStream,
    err: PrintStream,
    legacy: LegacyCommands,
    projects: ProjectCommands
  ): Int =
    args.headOption match
      case None =>
        err.print(Usage)
        2
      case Some("new") =>
        args.drop(1) match
          case Array(name) if !name.startsWith("-") =>
            projects.create(name, cwd, out, err)
          case _ =>
            usageError("new expects exactly one project name", err)
      case Some("build") =>
        optionalVerbose(args.drop(1)) match
          case Some(verbose) => projects.build(cwd, verbose, out, err)
          case None => usageError("build accepts only --verbose", err)
      case Some("run") =>
        parseRun(args.drop(1)) match
          case Some((verbose, programArgs)) =>
            projects.run(cwd, verbose, programArgs, out, err)
          case None =>
            usageError("run arguments must follow --", err)
      case Some("test") =>
        parseTest(args.drop(1)) match
          case Some((verbose, report)) => projects.test(cwd, verbose, out, err, report)
          case None => usageError("test accepts only --verbose and --report-xml <path>", err)
      case Some("clean") =>
        if args.length == 1 then projects.clean(cwd, out, err)
        else usageError("clean does not accept arguments", err)
      case Some("doc") =>
        projects.doc(cwd, args.drop(1), out, err)
      case Some("fmt") =>
        onion.tools.format.FormatCommand.run(cwd, args.drop(1), out, err)
      case Some("repl") =>
        legacy.repl(args.drop(1))
      case Some(_) =>
        legacy.script(args)

  /** `[--verbose] [--report-xml <path>]`, in either order. */
  private def parseTest(args: Array[String]): Option[(Boolean, Option[Path])] =
    var verbose = false
    var report: Option[Path] = None
    var i = 0
    while i < args.length do
      args(i) match
        case "--verbose" =>
          verbose = true
          i += 1
        case "--report-xml" =>
          if i + 1 >= args.length then return None
          report = Some(Paths.get(args(i + 1)))
          i += 2
        case _ => return None
    Some((verbose, report))

  private def optionalVerbose(args: Array[String]): Option[Boolean] =
    args match
      case Array() => Some(false)
      case Array("--verbose") => Some(true)
      case _ => None

  private def parseRun(args: Array[String]): Option[(Boolean, Array[String])] =
    args.toList match
      case Nil => Some((false, Array.empty))
      case "--verbose" :: Nil => Some((true, Array.empty))
      case "--" :: programArgs => Some((false, programArgs.toArray))
      case "--verbose" :: "--" :: programArgs => Some((true, programArgs.toArray))
      case _ => None

  private def usageError(message: String, err: PrintStream): Int =
    err.println(s"error: $message")
    err.println()
    err.print(Usage)
    2
