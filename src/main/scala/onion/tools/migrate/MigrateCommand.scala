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
 * code is 1. Each file is compiled on its own, against `-classpath` (default: none); a file
 * that compiles neither way alone is tried again compiled together with every named file.
 *
 * `--check` writes nothing and exits 1 if anything would change.
 *
 * Every file is re-lexed before it is written: the result must have the original's tokens and
 * comments, except the rewritten ones. Anything else would be a bug in a rule, and the file is
 * left alone and reported.
 */
object MigrateCommand:

  def run(cwd: Path, args: Array[String], out: PrintStream, err: PrintStream): Int =
    if args.exists(a => a == "--help" || a == "-h") then
      out.println(Usage)
      return 0
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
    val note = (file: Path, line: Int, column: Int, message: String) =>
      out.println(s"${display(file)}:$line:$column: note: $message")
    val compilesAsWritten = (file: Path, result: Migrator.Result) =>
      result.edits.foreach(e => note(file, e.line, e.column,
        "the file compiles as written, so this `ret` is a name, " +
          "not the old do-block keyword; left as written"))

    var changed = 0
    var undecided = 0
    var failed = false
    // The new text of each file, once decided; a file that is not here keeps its own.
    val decided = mutable.LinkedHashMap[Path, String]()
    // Files that compile neither way on their own: they may need the other named files.
    val pending = mutable.LinkedHashMap[Path, (String, Migrator.Result)]()

    def rewrite(file: Path, source: String, result: Migrator.Result): Unit =
      if !preserved(source, result) then
        err.println(s"error: Migrating ${display(file)} would change more than the rewritten tokens; left untouched")
        err.println("This is a bug in onion migrate. Please report it with the file.")
        failed = true
      else
        changed += 1
        decided(file) = result.text
        result.edits.foreach(e => out.println(s"${display(file)}:${e.line}:${e.column}: ${e.note}"))
        if !check then Files.writeString(file, result.text, UTF_8)

    def cannotTell(file: Path, result: Migrator.Result, problems: Seq[Compile.Problem]): Unit =
      undecided += 1
      failed = true
      result.edits.foreach { e =>
        err.println(s"${display(file)}:${e.line}:${e.column}: this `ret` may be the old do-block keyword. " +
          "The file does not compile with `yield` there either, so onion migrate left it as written.")
      }
      problems.headOption.foreach(p => err.println(s"  first error with `yield`: ${p.render(display)}"))
      err.println("  Fix that error and run onion migrate again, or change the `ret` by hand. " +
        "If the file uses classes from other files, name those files too, or pass -classpath.")

    files.foreach { file =>
      try
        val source = Files.readString(file, UTF_8)
        val result = Migrator.migrate(source)
        result.notes.foreach(n => note(file, n.line, n.column, n.message))
        if result.changed then
          if Compile.problems(Seq(file -> source), classpath).isEmpty then compilesAsWritten(file, result)
          else if Compile.problems(Seq(file -> result.text), classpath).isEmpty then rewrite(file, source, result)
          else pending(file) = (source, result)
      catch
        case error: IOException =>
          err.println(s"error: Could not migrate ${display(file)}: ${error.getMessage}")
          failed = true
        case _: StackOverflowError =>
          err.println(s"error: Could not migrate ${display(file)}: it nests too deeply; left untouched")
          failed = true
    }

    // A file may use classes another named file declares, so the undecided ones get a second
    // chance compiled together with every named file, with the same rule: a file that
    // compiles as written is left alone, and a rewrite is kept only if it compiles.
    if pending.nonEmpty then
      val unit = (rewritten: Set[Path]) => files.map { file =>
        pending.get(file) match
          case Some((source, result)) => file -> (if rewritten(file) then result.text else source)
          case None => file -> decided.getOrElse(file, Files.readString(file, UTF_8))
      }
      val failing = (problems: Seq[Compile.Problem], file: Path) =>
        problems.filter(p => p.file.forall(_ == file.toString))
      val asWritten = if files.size > 1 then Compile.problems(unit(Set.empty), classpath) else Seq()
      val candidates =
        if files.size > 1 then pending.keySet.filter(f => failing(asWritten, f).nonEmpty).toSet
        else pending.keySet.toSet
      val withYield = Compile.problems(unit(candidates), classpath)
      pending.foreach { case (file, (source, result)) =>
        if !candidates(file) then compilesAsWritten(file, result)
        else
          val left = failing(withYield, file)
          if left.isEmpty && files.size > 1 then rewrite(file, source, result)
          else cannotTell(file, result, if left.nonEmpty then left else withYield)
      }

    val upToDate = files.size - changed - undecided
    if check && changed > 0 then out.println(s"$changed file(s) would be migrated")
    else if changed > 0 then out.println(s"Migrated $changed of ${files.size} file(s)")
    else if upToDate > 0 || undecided == 0 then out.println(s"$upToDate file(s) already up to date")
    if undecided > 0 then out.println(s"$undecided file(s) need a manual change")

    if failed then 1
    else if check && changed > 0 then 1
    else 0

  /**
   * The migrated text lexes to the original tokens and comments, except that exactly as many
   * rewrites as there are edits were made inside them. A rewrite may sit inside a string
   * token, in the code of a `#{...}`, so a token is compared by walking both images.
   */
  private def preserved(source: String, result: Migrator.Result): Boolean =
    val before = Migrator.signature(source)
    val after = Migrator.signature(result.text)
    val rewrites = result.edits.map(e => (source.substring(e.offset, e.offset + e.length), e.replacement)).distinct
    /** How many rewrites turn `b` into `a`, or `None` if something else differs. */
    def rewritesBetween(b: String, a: String): Option[Int] =
      var i = 0
      var j = 0
      var count = 0
      var ok = true
      while ok && (i < b.length || j < a.length) do
        if i < b.length && j < a.length && b.charAt(i) == a.charAt(j) then
          i += 1
          j += 1
        else
          rewrites.find((from, to) => b.startsWith(from, i) && a.startsWith(to, j)) match
            case Some((from, to)) =>
              i += from.length
              j += to.length
              count += 1
            case None => ok = false
      if ok then Some(count) else None
    before.length == after.length && {
      val counts = before.zip(after).map((b, a) => rewritesBetween(b, a))
      counts.forall(_.isDefined) && counts.flatten.sum == result.edits.length
    }

  /** Compiles source texts together, writing nothing and running nothing. */
  private object Compile:
    /** One compile error; `file` is the name it was compiled under, when the error has one. */
    final case class Problem(file: Option[String], line: Int, column: Int, message: String):
      def render(display: Path => String): String =
        file.fold(message)(f => s"${display(Path.of(f))}:$line:$column: $message")

    /** The errors compiling `sources` together against `classpath`; empty when it compiles. */
    def problems(sources: Seq[(Path, String)], classpath: Seq[String]): Seq[Problem] =
      val config = CompilerConfig(classpath, null, "UTF-8", "", 1000,
        warningLevel = onion.compiler.WarningLevel.Off, checkLaws = false)
      try
        val inputs = sources.map((file, text) => StringInputSource(text, file.toString))
        new OnionCompiler(config).compileDetailed(inputs).allErrors.map { e =>
          val location = Option(e.location)
          Problem(Option(e.sourceFile), location.fold(0)(_.line), location.fold(0)(_.column), e.message)
        }
      catch
        case error: (Exception | StackOverflowError) =>
          Seq(Problem(None, 0, 0, s"the compiler failed: $error"))
