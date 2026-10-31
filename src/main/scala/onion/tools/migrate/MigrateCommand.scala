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
 * outside the file, so the compiler decides (the rule is written down on #2442). A file that
 * compiles on its own as written is never changed: its places are names, printed to stdout as
 * a `note:`. The others are compiled together with every named file that could be read,
 * against `-classpath` (default: none):
 *  - no error in the file: its places are names another file declares, noted and left alone;
 *  - every error in the joint compile, in any file, is at a place (`ret` not found, or not
 *    applicable), and with its places rewritten the file has no error: it is rewritten;
 *  - a file that does not parse as written, but does with its places rewritten, means
 *    nothing as written: it is read with them rewritten, and kept so if it then has no error;
 *  - anything else (an error anywhere that is not at a place, an error left after the rewrite, a
 *    compile stopped before type checking, a named file that could not be read or migrated): its places are printed to stderr with that
 *    error, the file is left as it was, the summary counts it as needing a manual change, and
 *    the exit code is 1.
 * An error that is not at a place proves nothing: it may come from another file named
 * alongside (a duplicate class), and change which names are found.
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
          // One file named under two spellings (`t6` and `./t6/Main.on`) is one file.
          val files = paths.flatMap(FormatCommand.expand)
            .map(_.toAbsolutePath.normalize)
            .distinctBy(file => try file.toRealPath() catch case _: IOException => file)
            .sortBy(_.toString)
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
    // Files that could not be read or migrated at all: never counted as up to date, and left
    // out of the joint compile, so while there is one it decides nothing.
    val leftOut = mutable.ListBuffer[Path]()
    var failed = false
    // The text each readable file has now: rewritten once decided, as read otherwise.
    val texts = mutable.LinkedHashMap[Path, String]()
    // Files that do not compile on their own as written: the joint compile decides them.
    val pending = mutable.LinkedHashMap[Path, (String, Migrator.Result)]()
    // Pending files that do not even parse as written, but do with `yield` (`ret [a, b]`): as
    // written they mean nothing, so the joint compile reads them with `yield` from the start.
    val unparsed = mutable.Set[Path]()

    def rewrite(file: Path, source: String, result: Migrator.Result): Unit =
      if !preserved(source, result) then
        err.println(s"error: Migrating ${display(file)} would change more than the rewritten tokens; left untouched")
        err.println("This is a bug in onion migrate. Please report it with the file.")
        failed = true
      else
        try
          if !check then Files.writeString(file, result.text, UTF_8)
          changed += 1
          texts(file) = result.text
          result.edits.foreach(e => out.println(s"${display(file)}:${e.line}:${e.column}: ${e.note}"))
        catch
          case error: IOException =>
            err.println(s"error: Could not write ${display(file)}: ${error.getMessage}")
            failed = true

    /** `stopped` is the error that kept the joint compile from type checking, if one did. */
    def cannotTell(
      file: Path,
      result: Migrator.Result,
      problems: Seq[Compile.Problem],
      stopped: Option[Compile.Problem] = None
    ): Unit =
      undecided += 1
      failed = true
      val why = stopped match
        case Some(_) => "The compiler reports another error, so it cannot tell, and onion migrate left it as written."
        case None => "The file does not compile with `yield` there either, so onion migrate left it as written."
      result.edits.foreach { e =>
        err.println(s"${display(file)}:${e.line}:${e.column}: this `ret` may be the old do-block keyword. " + why)
      }
      stopped match
        case Some(p) => err.println(s"  the other error: ${p.render(display)}")
        case None => problems.headOption.foreach(p => err.println(s"  first error with `yield`: ${p.render(display)}"))
      err.println("  Fix that error and run onion migrate again, or change the `ret` by hand. " +
        "If the file uses classes from other files, name those files too, or pass -classpath.")

    /** A named file was left out of the joint compile; it may declare this `ret`. */
    def notRead(file: Path, result: Migrator.Result): Unit =
      undecided += 1
      failed = true
      result.edits.foreach { e =>
        err.println(s"${display(file)}:${e.line}:${e.column}: this `ret` may be the old do-block keyword. " +
          "Another named file could not be migrated, and it may declare `ret`, so onion migrate left this one as written.")
      }
      leftOut.foreach(skippedFile => err.println(s"  left out: ${display(skippedFile)}"))
      err.println("  Fix the error reported for that file and run onion migrate again, or change the `ret` by hand.")

    files.foreach { file =>
      try
        val source = Files.readString(file, UTF_8)
        texts(file) = source
        val result = Migrator.migrate(source)
        result.notes.foreach(n => note(file, n.line, n.column, n.message))
        if result.changed then
          val alone = Compile.check(Seq(file -> source), classpath)
          if alone.problems.isEmpty then compilesAsWritten(file, result)
          else
            pending(file) = (source, result)
            if !alone.typed && Compile.check(Seq(file -> result.text), classpath).typed then unparsed += file
      catch
        case _: java.nio.charset.MalformedInputException =>
          err.println(s"error: Could not migrate ${display(file)}: it is not valid UTF-8; " +
            "onion migrate reads UTF-8 only. Convert the file to UTF-8 and run onion migrate again.")
          leftOut += file
          texts.remove(file)
          failed = true
        case error: IOException =>
          err.println(s"error: Could not migrate ${display(file)}: ${error.getMessage}")
          leftOut += file
          texts.remove(file)
          failed = true
        case _: StackOverflowError =>
          err.println(s"error: Could not migrate ${display(file)}: it nests too deeply; left untouched")
          leftOut += file
          texts.remove(file)
          failed = true
    }

    val failing = (problems: Seq[Compile.Problem], file: Path) =>
      problems.filter(p => p.file.forall(_ == file.toString))
    val atPlace = (p: Compile.Problem, result: Migrator.Result) =>
      p.file.nonEmpty && result.edits.exists(e => e.line == p.line && e.column == p.column)

    // A file may use classes, or a `ret`, another named file declares, so every undecided
    // file is compiled together with every named file that could be read: once as written,
    // once with the files that have errors rewritten. The first compile decides only if every
    // error in it, in any file, is at a place: any other error (a class two unrelated scripts
    // both declare, say) may be why a name is or is not found, so it proves nothing.
    if pending.nonEmpty && leftOut.nonEmpty then
      pending.foreach { case (file, (_, result)) => notRead(file, result) }
    else if pending.nonEmpty then
      val unit = (rewritten: Set[Path]) => texts.toSeq.map { (file, text) =>
        pending.get(file) match
          case Some((source, result)) => file -> (if rewritten(file) || unparsed(file) then result.text else source)
          case None => file -> text
      }
      val asWritten = Compile.check(unit(Set.empty), classpath)
      val other = asWritten.problems.find { p =>
        !pending.exists { case (file, (_, result)) => !unparsed(file) && p.file.contains(file.toString) && atPlace(p, result) }
      }
      val decides = asWritten.typed && other.isEmpty
      val candidates = pending.keySet.filter(file => !unparsed(file) && failing(asWritten.problems, file).nonEmpty).toSet
      val withYield = if decides && candidates.nonEmpty then Compile.check(unit(candidates), classpath) else asWritten
      pending.foreach { case (file, (source, result)) =>
        val mine = failing(asWritten.problems, file)
        if mine.isEmpty && !unparsed(file) && asWritten.typed then compilesAsWritten(file, result)
        else if !decides then cannotTell(file, result, asWritten.problems, other.orElse(asWritten.problems.headOption))
        else if unparsed(file) then
          val left = failing(withYield.problems, file)
          if left.isEmpty && withYield.typed then rewrite(file, source, result)
          else cannotTell(file, result, left)
        else
          val left = failing(withYield.problems, file)
          if left.nonEmpty then cannotTell(file, result, left)
          else if !withYield.typed then cannotTell(file, result, withYield.problems, withYield.problems.headOption)
          else rewrite(file, source, result)
      }

    val upToDate = files.size - changed - undecided - leftOut.size
    if check && changed > 0 then out.println(s"$changed file(s) would be migrated")
    else if changed > 0 then out.println(s"Migrated $changed of ${files.size} file(s)")
    else if upToDate > 0 || undecided + leftOut.size == 0 then out.println(s"$upToDate file(s) already up to date")
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

    /** The errors of one compile, and whether it got as far as type checking. */
    final case class Report(problems: Seq[Problem], typed: Boolean)

    /** The errors compiling `sources` together against `classpath`; empty when it compiles. */
    def problems(sources: Seq[(Path, String)], classpath: Seq[String]): Seq[Problem] =
      check(sources, classpath).problems

    def check(sources: Seq[(Path, String)], classpath: Seq[String]): Report =
      val config = CompilerConfig(classpath, null, "UTF-8", "", 1000,
        warningLevel = onion.compiler.WarningLevel.Off, checkLaws = false)
      try
        val inputs = sources.map((file, text) => StringInputSource(text, file.toString))
        val result = new OnionCompiler(config).compileDetailed(inputs)
        val problems = result.allErrors.map { e =>
          val location = Option(e.location)
          Problem(Option(e.sourceFile), location.fold(0)(_.line), location.fold(0)(_.column), e.message)
        }
        Report(problems, result.timings.exists(_.name == TypingPhaseName))
      catch
        case error: (Exception | StackOverflowError) =>
          Report(Seq(Problem(None, 0, 0, s"the compiler failed: $error")), false)

    private val TypingPhaseName = "Typing"
