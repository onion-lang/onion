package onion.tools.project

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

import scala.util.control.NonFatal

class ProjectCommands:
  def create(name: String, cwd: Path, out: PrintStream, err: PrintStream): Int =
    ProjectScaffolder.create(cwd, name) match
      case Right(paths) =>
        out.println(s"Created ${paths.root}")
        0
      case Left(error) =>
        err.println(s"error: ${error.message}")
        1

  def build(cwd: Path, verbose: Boolean, out: PrintStream, err: PrintStream): Int =
    buildProject(cwd, err) match
      case Right(build) =>
        if build.cached then out.println(s"Built ${build.manifest.name} (cached)")
        else out.println(s"Built ${build.manifest.name} (${build.state.classes.size} classes)")
        0
      case Left(error) =>
        err.println(s"error: ${error.message}")
        1

  def run(
    cwd: Path,
    verbose: Boolean,
    args: Array[String],
    out: PrintStream,
    err: PrintStream
  ): Int =
    buildProject(cwd, err) match
      case Left(error) =>
        err.println(s"error: ${error.message}")
        1
      case Right(build) =>
        val entryPoints = build.state.entryPoints.sortBy(entryPoint =>
          (entryPoint.source, entryPoint.line, entryPoint.column, entryPoint.className)
        )
        entryPoints match
          case Vector() =>
            err.println(
              "error: Project has no entrypoint; add executable top-level code " +
                "to src/main.on or define a top-level main function"
            )
            1
          case Vector(entryPoint) =>
            ProjectClassRunner.run(
              // The dependency jars have to be on the *run* classpath too, not only the
              // compile one, or every project that compiles against a library dies with
              // NoClassDefFoundError the moment it touches it.
              build.paths.classes +: build.dependencies.classpath.toVector,
              entryPoint.className,
              args
            ) match
              case ProgramResult.Success(value) =>
                ProjectCommands.programExitCode(value)
              case ProgramResult.Failure(message, cause) =>
                err.println(s"error: $message")
                if verbose then cause.foreach(_.printStackTrace(err))
                1
          case candidates =>
            err.println("error: Project has multiple entrypoints:")
            candidates.foreach { entryPoint =>
              err.println(
                s"  ${entryPoint.source}:${entryPoint.line}:${entryPoint.column} " +
                  s"(${entryPoint.className})"
              )
            }
            1

  def test(cwd: Path, verbose: Boolean, out: PrintStream, err: PrintStream): Int =
    buildProject(cwd, err) match
      case Left(error) =>
        err.println(s"error: ${error.message}")
        1
      case Right(build) =>
        val result = ProjectTestRunner().run(build, out, err, verbose)
        if result.successful then 0 else 1

  def clean(cwd: Path, out: PrintStream, err: PrintStream): Int =
    (for
      paths <- ProjectLocator.locate(cwd)
      _ <- ProjectManifest.load(paths.manifest)
      _ <- cleanTarget(paths)
    yield ()) match
      case Right(_) =>
        out.println("Cleaned target")
        0
      case Left(error) =>
        err.println(s"error: ${error.message}")
        1

  private def cleanTarget(paths: ProjectPaths): Either[ProjectError, Unit] =
    if Files.isSymbolicLink(paths.target) then
      Left(ProjectError(s"Project target path is a symbolic link: ${paths.target}"))
    else
      try
        FileTree.delete(paths.target, paths.root)
        Right(())
      catch
        case NonFatal(error) =>
          Left(ProjectError(s"Could not clean target: ${error.getMessage}", Some(error)))

  /**
   * `onion doc` — API documentation.
   *
   * `OnionDoc` has been a complete generator for a while (six files, HTML output, its own
   * `-d` and `--help`) with no way to run it: no launcher, no subcommand, no build task.
   * A feature nobody can invoke is indistinguishable from one that does not exist.
   *
   * Inside a project with no files named, it documents the production sources into
   * `target/doc`, so the common case is `onion doc` and nothing else.
   */
  def doc(cwd: Path, args: Array[String], out: PrintStream, err: PrintStream): Int =
    parseDocArgs(args) match
      case Left(message) =>
        err.println(s"error: $message")
        2
      case Right((explicitOut, sources)) if sources.nonEmpty =>
        generateDoc(explicitOut.getOrElse("doc"), sources, out, err)
      case Right((explicitOut, _)) =>
        // No files given: fall back to the project's own sources.
        val located =
          for
            paths <- ProjectLocator.locate(cwd)
            layout <- ProjectLayout.discover(paths)
          yield (paths, layout)
        located match
          case Left(error) =>
            err.println(s"error: ${error.message}")
            err.println("Name source files explicitly, or run inside a project.")
            2
          case Right((paths, layout)) if layout.productionSources.isEmpty =>
            err.println("error: Project has no production sources")
            1
          case Right((paths, layout)) =>
            val target = explicitOut.getOrElse(paths.target.resolve("doc").toString)
            generateDoc(target, layout.productionSources.map(_.path.toString), out, err)

  private def generateDoc(
    outDir: String,
    sources: Seq[String],
    out: PrintStream,
    err: PrintStream
  ): Int =
    val exit = onion.tools.doc.OnionDoc.run((Array("-d", outDir) ++ sources))
    if exit == 0 then out.println(s"Documented ${sources.size} source(s) into $outDir")
    exit

  /** `[-d <dir>] [<source.on>...]`, kept deliberately small. */
  private def parseDocArgs(args: Array[String]): Either[String, (Option[String], Seq[String])] =
    var outDir: Option[String] = None
    val sources = scala.collection.mutable.ListBuffer[String]()
    var i = 0
    while i < args.length do
      args(i) match
        case "-d" =>
          if i + 1 >= args.length then return Left("doc: -d requires an output directory")
          outDir = Some(args(i + 1))
          i += 2
        case flag if flag.startsWith("-") =>
          return Left(s"doc: unknown option: $flag")
        case path =>
          sources += path
          i += 1
    Right((outDir, sources.toSeq))

  private def buildProject(
    cwd: Path,
    err: PrintStream
  ): Either[ProjectError, ProjectBuild] =
    for
      paths <- ProjectLocator.locate(cwd)
      manifest <- ProjectManifest.load(paths.manifest)
      layout <- ProjectLayout.discover(paths)
      build <- ProjectBuilder().build(paths, manifest, layout, err)
    yield build

object ProjectCommands:
  private[project] def programExitCode(value: Any): Int =
    // Compiler-generated zero-argument/scalar auto-CLI wrappers return void.
    // A numeric value reaches here only from a directly invokable raw-argv main.
    value match
      case number: java.lang.Number if !numericZero(number) => 1
      case _ => 0

  private def numericZero(number: java.lang.Number): Boolean =
    number match
      case value: scala.math.BigInt => value.signum == 0
      case value: scala.math.BigDecimal => value.signum == 0
      case value: java.math.BigDecimal => value.signum() == 0
      case value: java.math.BigInteger => value.signum() == 0
      case value: java.lang.Byte => value.byteValue() == 0
      case value: java.lang.Short => value.shortValue() == 0
      case value: java.lang.Integer => value.intValue() == 0
      case value: java.lang.Long => value.longValue() == 0L
      case value: java.lang.Float => value.floatValue() == 0.0f
      case value: java.lang.Double => value.doubleValue() == 0.0d
      case _ => false
