package onion.tools.migrate

import java.io.{IOException, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import scala.collection.mutable

import onion.tools.format.FormatCommand

/**
 * `onion migrate [--check] <path>...`.
 *
 * Rewrites sources written before a pre-1.0 breaking change so they compile today, in place,
 * and prints every change as `file:line:column: what`. The rules are in [[Migrator]]; the
 * migration guide (`docs/migration/pre-1.0.md`) names the command for each change it covers.
 *
 * Paths are required: a tool that edits code should not pick its own targets. A directory is
 * walked for `.on` files, as `onion fmt` does.
 *
 * `--check` writes nothing and exits 1 if anything would change. A place a rule cannot decide
 * is printed to stderr and makes the exit code 1, and that file is left as it was.
 *
 * Every file is re-lexed before it is written: the result must have the original's tokens and
 * comments, except the rewritten ones. Anything else would be a bug in a rule, and the file is
 * left alone and reported.
 */
object MigrateCommand:

  def run(cwd: Path, args: Array[String], out: PrintStream, err: PrintStream): Int =
    parse(args) match
      case Left(message) =>
        err.println(s"error: $message")
        err.println("Usage: onion migrate [--check] <path>...")
        2
      case Right((check, paths)) =>
        val missing = paths.filterNot(Files.exists(_))
        if missing.nonEmpty then
          missing.foreach(path => err.println(s"error: No such file or directory: $path"))
          2
        else
          val files = paths.flatMap(FormatCommand.expand).distinct.sortBy(_.toString)
          if files.isEmpty then
            err.println("error: No .on files to migrate")
            1
          else migrate(files, check, cwd, out, err)

  private def parse(args: Array[String]): Either[String, (Boolean, Seq[Path])] =
    val paths = mutable.ListBuffer[Path]()
    var check = false
    args.foreach {
      case "--check" => check = true
      case flag if flag.startsWith("-") => return Left(s"migrate: unknown option: $flag")
      case path => paths += Path.of(path)
    }
    if paths.isEmpty then Left("migrate: name the files or directories to migrate")
    else Right((check, paths.toSeq))

  private def migrate(
    files: Seq[Path],
    check: Boolean,
    cwd: Path,
    out: PrintStream,
    err: PrintStream
  ): Int =
    val display = (path: Path) =>
      val relative = try cwd.relativize(path.toAbsolutePath) catch case _: IllegalArgumentException => path
      relative.toString

    var changed = 0
    var failed = false
    files.foreach { file =>
      try
        val source = Files.readString(file, UTF_8)
        val result = Migrator.migrate(source)
        result.unsure.foreach { u =>
          err.println(s"${display(file)}:${u.line}:${u.column}: ${u.message}")
          failed = true
        }
        if result.changed then
          if !preserved(source, result) then
            err.println(s"error: Migrating ${display(file)} would change more than the rewritten tokens; left untouched")
            err.println("This is a bug in onion migrate. Please report it with the file.")
            failed = true
          else
            changed += 1
            result.edits.foreach(e => out.println(s"${display(file)}:${e.line}:${e.column}: ${e.note}"))
            if !check then Files.writeString(file, result.text, UTF_8)
      catch
        case error: IOException =>
          err.println(s"error: Could not migrate ${display(file)}: ${error.getMessage}")
          failed = true
    }

    if check then
      if changed > 0 then out.println(s"$changed file(s) would be migrated")
      else out.println(s"${files.size} file(s) already up to date")
    else if changed == 0 then out.println(s"${files.size} file(s) already up to date")
    else out.println(s"Migrated $changed of ${files.size} file(s)")

    if failed then 1
    else if check && changed > 0 then 1
    else 0

  /** The migrated text lexes to the original tokens, with only the rewritten ones replaced. */
  private def preserved(source: String, result: Migrator.Result): Boolean =
    val before = Migrator.signature(source)
    val after = Migrator.signature(result.text)
    val expected = result.edits.sortBy(_.offset).map(e =>
      (source.substring(e.offset, e.offset + e.length), e.replacement))
    before.length == after.length && before.zip(after).filter(_ != _) == expected
