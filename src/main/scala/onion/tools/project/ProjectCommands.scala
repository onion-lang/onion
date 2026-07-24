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
    val result =
      for
        paths <- ProjectLocator.locate(cwd)
        manifest <- ProjectManifest.load(paths.manifest)
        layout <- ProjectLayout.discover(paths)
        build <- ProjectBuilder().build(paths, manifest, layout, err)
      yield build

    result match
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
    notImplemented("run", err)

  def test(cwd: Path, verbose: Boolean, out: PrintStream, err: PrintStream): Int =
    notImplemented("test", err)

  def clean(cwd: Path, out: PrintStream, err: PrintStream): Int =
    notImplemented("clean", err)

  private def notImplemented(command: String, err: PrintStream): Int =
    err.println(s"error: onion $command is not implemented yet")
    1
