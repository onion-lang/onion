package onion.tools.format

import java.io.{IOException, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*
import scala.collection.mutable

import onion.tools.project.{ProjectLayout, ProjectLocator}

/**
 * `onion fmt [--check] [<path>...]`.
 *
 * With no paths it formats the project's own sources, so the common case is a bare `onion
 * fmt`. A path may be a file or a directory, and a directory is walked for `.on` files —
 * a formatter people have to feed one filename at a time does not get run.
 *
 * `--check` writes nothing and exits 1 if anything would change, which is the shape CI
 * needs. It lists the files rather than only counting them: "3 files are not formatted" is
 * not actionable from a build log.
 *
 * Every file is verified before it is written: the formatted text must lex to the same
 * token and comment stream as the original, or that file is left alone and reported. The
 * formatter is whitespace-only by construction, so a difference here means a bug in it —
 * and the one thing a formatter must never do is quietly damage a source file. The check
 * costs one extra lex per changed file.
 */
object FormatCommand:

  def run(cwd: Path, args: Array[String], out: PrintStream, err: PrintStream): Int =
    parse(args) match
      case Left(message) =>
        err.println(s"error: $message")
        2
      case Right(options) =>
        collect(cwd, options.paths, err) match
          case Left(code) => code
          case Right(files) if files.isEmpty =>
            err.println("error: No .on files to format")
            1
          case Right(files) => format(files, options.check, cwd, out, err)

  private final case class Options(check: Boolean, paths: Seq[Path])

  private def parse(args: Array[String]): Either[String, Options] =
    val paths = mutable.ListBuffer[Path]()
    var check = false
    args.foreach {
      case "--check" => check = true
      case flag if flag.startsWith("-") => return Left(s"fmt: unknown option: $flag")
      case path => paths += Path.of(path)
    }
    Right(Options(check, paths.toSeq))

  /** The files to format: the ones named, or the project's own if none were. */
  private def collect(cwd: Path, paths: Seq[Path], err: PrintStream): Either[Int, Seq[Path]] =
    if paths.nonEmpty then
      val missing = paths.filterNot(Files.exists(_))
      if missing.nonEmpty then
        missing.foreach(path => err.println(s"error: No such file or directory: $path"))
        Left(2)
      else Right(paths.flatMap(expand).distinct.sortBy(_.toString))
    else
      val located =
        for
          projectPaths <- ProjectLocator.locate(cwd)
          layout <- ProjectLayout.discover(projectPaths)
        yield layout
      located match
        case Left(error) =>
          err.println(s"error: ${error.message}")
          err.println("Name files or directories explicitly, or run inside a project.")
          Left(2)
        case Right(layout) =>
          Right((layout.productionSources ++ layout.testSources).map(_.path).sortBy(_.toString))

  private def expand(path: Path): Seq[Path] =
    if Files.isDirectory(path) then
      val stream = Files.walk(path)
      try
        stream.iterator().asScala
          .filter(Files.isRegularFile(_))
          .filter(_.getFileName.toString.endsWith(".on"))
          .toVector
      finally stream.close()
    else Seq(path)

  private def format(
    files: Seq[Path],
    check: Boolean,
    cwd: Path,
    out: PrintStream,
    err: PrintStream
  ): Int =
    val changed = mutable.ListBuffer[Path]()
    val damaged = mutable.ListBuffer[Path]()
    var failed = false

    files.foreach { file =>
      try
        val source = Files.readString(file, UTF_8)
        val result = OnionFormatter.format(source)
        if result.changed then
          if OnionFormatter.signature(result.text) != OnionFormatter.signature(source) then
            damaged += file
          else
            changed += file
            if !check then Files.writeString(file, result.text, UTF_8)
      catch
        case error: IOException =>
          err.println(s"error: Could not format $file: ${error.getMessage}")
          failed = true
    }

    damaged.foreach { file =>
      err.println(s"error: Formatting $file would change its tokens; left untouched")
      err.println("This is a bug in the formatter. Please report it with the file.")
    }

    val display = (path: Path) =>
      val relative = try cwd.relativize(path.toAbsolutePath) catch case _: IllegalArgumentException => path
      relative.toString

    if check then
      changed.foreach(file => out.println(display(file)))
      if changed.nonEmpty then
        out.println(s"${changed.size} file(s) would be reformatted")
    else if changed.isEmpty then out.println(s"${files.size} file(s) already formatted")
    else out.println(s"Formatted ${changed.size} of ${files.size} file(s)")

    if failed || damaged.nonEmpty then 1
    else if check && changed.nonEmpty then 1
    else 0
