package onion.tools.migrate

import java.io.{IOException, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import scala.collection.mutable

import onion.compiler.{CompilerConfig, OnionCompiler, StringInputSource}
import onion.tools.format.FormatCommand

/**
 * `onion migrate [--check] [-classpath <path>] <path>...`.
 *
 * Rewrites sources written before a pre-1.0 breaking change so they compile today, in place,
 * and prints every change as `file:line:column: what`. The rules are in [[Migrator]]; the
 * migration guide (`docs/migration/pre-1.0.md`) names the command for each change it covers.
 *
 * Paths are required: a tool that edits code should not pick its own targets. A directory is
 * walked for `.on` files, as `onion fmt` does.
 *
 * Whether a place a rule found really is the old spelling can depend on names declared
 * outside the file, so the compiler decides. A file that compiles as written is never
 * changed: its places are names, printed to stdout as a `note:`. A file that does not is
 * rewritten only if the rewritten file compiles. Otherwise its places are printed to stderr,
 * the file is left as it was, the summary counts it as needing a manual change, and the exit
 * code is 1. Each file is compiled on its own, against `-classpath` (default: none).
 *
 * `--check` writes nothing and exits 1 if anything would change.
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
        err.println(Usage)
        2
      case Right((check, classpath, paths)) =>
        val missing = paths.filterNot(Files.exists(_))
        if missing.nonEmpty then
          missing.foreach(path => err.println(s"error: No such file or directory: $path"))
          2
        else
          val files = paths.flatMap(FormatCommand.expand).distinct.sortBy(_.toString)
          if files.isEmpty then
            err.println("error: No .on files to migrate")
            1
          else migrate(files, check, classpath, cwd, out, err)

  private val Usage = "Usage: onion migrate [--check] [-classpath <path>] <path>..."

  private def parse(args: Array[String]): Either[String, (Boolean, Seq[String], Seq[Path])] =
    val paths = mutable.ListBuffer[Path]()
    var check = false
    var classpath = Seq.empty[String]
    var i = 0
    while i < args.length do
      args(i) match
        case "--check" => check = true
        case "-classpath" | "-cp" =>
          if i + 1 >= args.length then return Left(s"migrate: ${args(i)} needs a path")
          i += 1
          classpath = args(i).split(java.io.File.pathSeparator).toSeq.filter(_.nonEmpty)
        case flag if flag.startsWith("-") => return Left(s"migrate: unknown option: $flag")
        case path => paths += Path.of(path)
      i += 1
    if paths.isEmpty then Left("migrate: name the files or directories to migrate")
    else Right((check, classpath, paths.toSeq))

  private def migrate(
    files: Seq[Path],
    check: Boolean,
    classpath: Seq[String],
    cwd: Path,
    out: PrintStream,
    err: PrintStream
  ): Int =
    val display = (path: Path) =>
      val relative = try cwd.relativize(path.toAbsolutePath) catch case _: IllegalArgumentException => path
      relative.toString

    val compiles = (text: String, file: Path) => Compile.succeeds(text, file, classpath)

    var changed = 0
    var undecided = 0
    var failed = false
    files.foreach { file =>
      try
        val source = Files.readString(file, UTF_8)
        val result = Migrator.migrate(source)
        val note = (line: Int, column: Int, message: String) =>
          out.println(s"${display(file)}:$line:$column: note: $message")
        result.notes.foreach(n => note(n.line, n.column, n.message))
        if result.changed then
          if compiles(source, file) then
            result.edits.foreach(e => note(e.line, e.column,
              "the file compiles as written, so this `ret` is a name, " +
                "not the old do-block keyword; left as written"))
          else if !compiles(result.text, file) then
            undecided += 1
            failed = true
            result.edits.foreach(e => err.println(s"${display(file)}:${e.line}:${e.column}: " +
              "this `ret` may be the old do-block keyword, but the file does not compile with " +
              "`yield` there either, so onion migrate cannot tell; fix the other errors " +
              "(or pass -classpath) and run it again, or change it by hand"))
          else if !preserved(source, result) then
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

    val upToDate = files.size - changed - undecided
    if check && changed > 0 then out.println(s"$changed file(s) would be migrated")
    else if changed > 0 then out.println(s"Migrated $changed of ${files.size} file(s)")
    else if upToDate > 0 || undecided == 0 then out.println(s"$upToDate file(s) already up to date")
    if undecided > 0 then out.println(s"$undecided file(s) need a manual change")

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

  /** Compiles one file's text on its own, writing nothing and running nothing. */
  private object Compile:
    def succeeds(text: String, file: Path, classpath: Seq[String]): Boolean =
      val config = CompilerConfig(classpath, null, "UTF-8", "", 1,
        warningLevel = onion.compiler.WarningLevel.Off, checkLaws = false)
      try !new OnionCompiler(config).compileDetailed(Seq(StringInputSource(text, file.toString))).hasErrors
      catch case _: Exception => false
