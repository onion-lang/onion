package onion.tools.project

import java.io.PrintStream
import java.nio.file.Path

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
              Vector(build.paths.classes),
              entryPoint.className,
              args
            ) match
              case ProgramResult.Success(value) =>
                value match
                  case number: java.lang.Number if !numericZero(number) => 1
                  case _ => 0
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
    notImplemented("test", err)

  def clean(cwd: Path, out: PrintStream, err: PrintStream): Int =
    notImplemented("clean", err)

  private def notImplemented(command: String, err: PrintStream): Int =
    err.println(s"error: onion $command is not implemented yet")
    1

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

  private def numericZero(number: java.lang.Number): Boolean =
    number match
      case value: java.math.BigDecimal => value.signum() == 0
      case value: java.math.BigInteger => value.signum() == 0
      case value: java.lang.Byte => value.byteValue() == 0
      case value: java.lang.Short => value.shortValue() == 0
      case value: java.lang.Integer => value.intValue() == 0
      case value: java.lang.Long => value.longValue() == 0L
      case value: java.lang.Float => value.floatValue() == 0.0f
      case value: java.lang.Double => value.doubleValue() == 0.0d
      case value => value.doubleValue() == 0.0d
