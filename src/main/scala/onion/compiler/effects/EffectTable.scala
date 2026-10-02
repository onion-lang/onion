package onion.compiler.effects

import java.io.{BufferedReader, File, InputStream, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * The out-of-band effect table (issue #356).
 *
 * Effects cannot ride on annotations here: the `Method` trait carries none, nothing is
 * emitted to bytecode, and Java methods never reach the type checker with theirs (audit
 * §7.4). So the facts live in a resource — `onion/effect-table.txt` — same shipping
 * shape as `default-static-imports.txt`, and this object is its loader.
 *
 * Line format (after `#`/`//` comments and blanks are dropped):
 *
 * {{{
 *   fully.qualified.Class#method=effect[,effect...]   // one method
 *   fully.qualified.Class#*=effect[,effect...]        // every method of the class
 *   fully.qualified.Class#method=pure                 // explicitly no effects
 *   fully.qualified.Class#method=net:api.example.com,env:API_KEY   // fixed operands
 * }}}
 *
 * An effect may carry a fixed operand, `effect:operand`: free text up to the next comma
 * (trimmed, non-empty). The operand is what `--plan` and the contract's `staticOperands`
 * show for every call of that method, exactly like an operand read off a literal at the
 * call site (see [[StaticOperands]]). `pure` and `unknown` take no operand.
 *
 * A specific `Class#method` entry beats the class's `Class#*` wildcard, which is how a
 * mostly-pure class declares its exceptions (`onion.DateTime#*=pure` + `#now=clock`).
 *
 * '''Library tables.''' A jar on the compile classpath may ship its own table at
 * [[LibraryResourcePath]] (`META-INF/onion/effect-table.txt`), same format. Per
 * compilation, [[forClasspath]] merges those with the built-in table into a [[Table]]:
 *
 *   - a library may classify only classes whose class file is in the same jar; lines
 *     naming any other class are dropped (with one W0018 per jar)
 *   - a library table that does not parse is dropped whole (W0017, naming jar and line):
 *     dropping one line could leave a more permissive wildcard in charge of its method
 *   - for `onion.*` and `java.*` classes the built-in table wins whenever it has an
 *     entry; otherwise the library's entry is consulted first
 *   - between libraries, the first jar on the classpath that classifies a class owns it
 *     (the jar the JVM would load the class from)
 *
 * Parsed jar tables are cached per jar path, size and modification time.
 *
 * `lookup` returns `None` for anything the table does not cover. The caller decides
 * what that means: for a Java method it means [[Effect.Unknown]] (not *known* pure);
 * for an Onion-defined method it means "infer from the body" (see [[EffectInference]]).
 */
object EffectTable {
  private val ResourcePath = "onion/effect-table.txt"

  /** Where a library jar ships its table. */
  val LibraryResourcePath = "META-INF/onion/effect-table.txt"

  /** A table line that does not parse: `source` is the file (or `jar!/path`), `line` 1-based. */
  final class MalformedTableException(val source: String, val line: Int, val detail: String)
    extends RuntimeException(s"$source:$line: $detail")

  final case class Parsed(exact: Map[(String, String), Set[Effect]],
                          wildcard: Map[String, Set[Effect]],
                          exactOperands: Map[(String, String), Seq[(Effect, String)]] = Map.empty,
                          wildcardOperands: Map[String, Seq[(Effect, String)]] = Map.empty,
                          firstLine: Map[String, Int] = Map.empty) {
    /** Every class the table classifies. */
    def classes: Set[String] = wildcard.keySet ++ exact.keySet.map(_._1)

    def lookup(className: String, methodName: String): Option[Set[Effect]] =
      exact.get((className, methodName)).orElse(wildcard.get(className))

    /** The fixed operands of the entry [[lookup]] would use. */
    def operands(className: String, methodName: String): Seq[(Effect, String)] =
      if (exact.contains((className, methodName))) exactOperands.getOrElse((className, methodName), Nil)
      else wildcardOperands.getOrElse(className, Nil)

    /** This table with only the classes `keep` accepts. */
    def restrictTo(keep: String => Boolean): Parsed =
      Parsed(exact.filter { case ((c, _), _) => keep(c) }, wildcard.filter { case (c, _) => keep(c) },
        exactOperands.filter { case ((c, _), _) => keep(c) }, wildcardOperands.filter { case (c, _) => keep(c) },
        firstLine.filter { case (c, _) => keep(c) })
  }

  private val Empty = Parsed(Map.empty, Map.empty)

  /** Parses table lines; malformed lines fail loudly — a silently dropped entry would
   *  turn into a silently wrong `unknown`/`pure` verdict downstream. Throws
   *  [[MalformedTableException]] naming `source` and the line. */
  def parseLines(lines: Iterator[String], source: String = ResourcePath): Parsed = {
    var exact = Map.empty[(String, String), Set[Effect]]
    var wildcard = Map.empty[String, Set[Effect]]
    var exactOps = Map.empty[(String, String), Seq[(Effect, String)]]
    var wildcardOps = Map.empty[String, Seq[(Effect, String)]]
    var firstLine = Map.empty[String, Int]
    for ((raw, idx) <- lines.zipWithIndex) {
      val line = raw.trim
      def fail(detail: String): Nothing = throw new MalformedTableException(source, idx + 1, detail)
      if (line.nonEmpty && !line.startsWith("#") && !line.startsWith("//")) {
        val (spec, rhs) = line.split("=", 2) match {
          case Array(l, r) => (l.trim, r.trim)
          case _ => fail(s"missing '=': $line")
        }
        val (cls, method) = spec.split("#", 2) match {
          case Array(c, m) if c.nonEmpty && m.nonEmpty => (c, m)
          case _ => fail(s"expected Class#method: $line")
        }
        val (effects, operands): (Set[Effect], Seq[(Effect, String)]) =
          if (rhs == "pure") (Set.empty, Nil)
          else {
            val parts = rhs.split(",", -1).toSeq.map(_.trim).map { item =>
              val colon = item.indexOf(':')
              val (name, operand) =
                if (colon < 0) (item, None)
                else (item.substring(0, colon).trim, Some(item.substring(colon + 1).trim))
              val effect = Effect.parse(name).getOrElse(
                fail(s"unknown effect '$name' (know: ${Effect.all.mkString(", ")})"))
              operand match {
                case Some("") => fail(s"empty operand after '$name:': $line")
                case Some(_) if effect == Effect.Unknown => fail(s"'unknown' takes no operand: $line")
                case _ =>
              }
              (effect, operand)
            }
            (parts.map(_._1).toSet, parts.collect { case (e, Some(op)) => (e, op) }.distinct)
          }
        if (!firstLine.contains(cls)) firstLine += (cls -> (idx + 1))
        if (method == "*") {
          wildcard += (cls -> effects)
          if (operands.nonEmpty) wildcardOps += (cls -> operands) else wildcardOps -= cls
        } else {
          exact += ((cls, method) -> effects)
          if (operands.nonEmpty) exactOps += ((cls, method) -> operands) else exactOps -= ((cls, method))
        }
      }
    }
    Parsed(exact, wildcard, exactOps, wildcardOps, firstLine)
  }

  private def readLines(input: InputStream, source: String): Parsed = {
    val reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
    try parseLines(Iterator.continually(reader.readLine()).takeWhile(_ != null), source)
    finally reader.close()
  }

  private lazy val builtinParsed: Parsed = {
    val loader = Option(Thread.currentThread.getContextClassLoader).getOrElse(getClass.getClassLoader)
    Option(loader.getResourceAsStream(ResourcePath)) match {
      case None => Empty  // degraded: everything Java-side is Unknown
      case Some(input) => readLines(input, ResourcePath)
    }
  }

  /** Classes whose built-in entry beats any library's. */
  def builtinFirst(className: String): Boolean =
    className.startsWith("onion.") || className.startsWith("java.")

  /**
   * The table one compilation consults: the built-in entries plus each library jar's.
   * `libraries` are in classpath order and already restricted to their own classes.
   */
  final class Table private[EffectTable] (builtin: Parsed, libraries: Seq[Parsed]) {
    /** The library that owns each library-classified class: the first that names it. */
    private val owner: Map[String, Parsed] = {
      val b = Map.newBuilder[String, Parsed]
      var seen = Set.empty[String]
      for (lib <- libraries; cls <- lib.classes if !seen.contains(cls)) { seen += cls; b += cls -> lib }
      b.result()
    }

    private def source(className: String, methodName: String): Option[Parsed] = {
      val fromBuiltin = Some(builtin).filter(_.lookup(className, methodName).isDefined)
      def fromLibrary = owner.get(className).filter(_.lookup(className, methodName).isDefined)
      if (builtinFirst(className)) fromBuiltin.orElse(fromLibrary) else fromLibrary.orElse(fromBuiltin)
    }

    /** The table's verdict for `className#methodName`, or `None` if it has none. */
    def lookup(className: String, methodName: String): Option[Set[Effect]] =
      source(className, methodName).flatMap(_.lookup(className, methodName))

    /** The fixed operands (`net:host`) of the entry [[lookup]] uses; empty when none. */
    def operands(className: String, methodName: String): Seq[(Effect, String)] =
      source(className, methodName).map(_.operands(className, methodName)).getOrElse(Nil)

    def effectsOrUnknown(className: String, methodName: String): Set[Effect] =
      lookup(className, methodName).getOrElse(Set(Effect.Unknown))
  }

  /** The built-in table alone: what a compilation with no table-shipping jar consults. */
  lazy val builtin: Table = new Table(builtinParsed, Nil)

  /** The table's verdict for `className#methodName`, or `None` if it has none. */
  def lookup(className: String, methodName: String): Option[Set[Effect]] =
    builtin.lookup(className, methodName)

  /** Convenience for the `Method`-trait default: unlisted means not-known-pure. */
  def effectsOrUnknown(className: String, methodName: String): Set[Effect] =
    builtin.effectsOrUnknown(className, methodName)

  // ---- library tables ------------------------------------------------------------

  /** Why some or all of a library table was not used. */
  enum ProblemKind {
    /** The table does not parse; the whole table was ignored (W0017). */
    case Malformed
    /** Lines name classes the jar does not contain; those lines were ignored (W0018). */
    case ForeignClass
  }

  /** `source` is `<jar>!/META-INF/onion/effect-table.txt`; `line` is 1-based. */
  final case class Problem(kind: ProblemKind, jar: String, source: String, line: Int, message: String)

  final case class Loaded(table: Table, problems: Seq[Problem])

  private final case class JarKey(size: Long, modified: Long)
  private final case class JarResult(table: Option[Parsed], problems: Seq[Problem])
  private val jarCache = new ConcurrentHashMap[String, (JarKey, JarResult)]()

  /**
   * The table for a compilation whose classpath is `classPath`: the built-in one plus
   * whatever table-shipping jars it lists (directories are not consulted). Never throws:
   * an unreadable jar contributes nothing, a malformed table is reported in `problems`.
   */
  def forClasspath(classPath: Seq[String]): Loaded = {
    val entries = classPath.flatMap(_.split(File.pathSeparator, -1)).filter(_.nonEmpty).distinct
    val results = entries.flatMap(libraryTable)
    val tables = results.flatMap(_.table)
    Loaded(if (tables.isEmpty) builtin else new Table(builtinParsed, tables), results.flatMap(_.problems))
  }

  private def libraryTable(path: String): Option[JarResult] = {
    val file = new File(path)
    if (!file.isFile) return None
    val key = JarKey(file.length, file.lastModified)
    val canonical = scala.util.Try(file.getCanonicalPath).getOrElse(file.getAbsolutePath)
    val cached = jarCache.get(canonical)
    if (cached != null && cached._1 == key) return Some(cached._2)
    val result = readJar(file, path)
    jarCache.put(canonical, (key, result))
    Some(result)
  }

  private def readJar(file: File, displayPath: String): JarResult = {
    val zip =
      try new java.util.zip.ZipFile(file)
      catch { case _: java.io.IOException => return JarResult(None, Nil) } // not a jar
    try {
      val entry = zip.getEntry(LibraryResourcePath)
      if (entry == null) return JarResult(None, Nil)
      val source = s"$displayPath!/$LibraryResourcePath"
      val parsed =
        try readLines(zip.getInputStream(entry), source)
        catch {
          case e: MalformedTableException =>
            return JarResult(None, Seq(Problem(ProblemKind.Malformed, displayPath, source, e.line,
              s"library effect table ignored: ${e.detail}")))
          case e: java.io.IOException =>
            return JarResult(None, Seq(Problem(ProblemKind.Malformed, displayPath, source, 1,
              s"library effect table ignored: cannot read it (${e.getMessage})")))
        }
      val inJar: String => Boolean = cls => zip.getEntry(cls.replace('.', '/') + ".class") != null
      val foreign = parsed.classes.filterNot(inJar).toSeq.sortBy(c => (parsed.firstLine.getOrElse(c, 0), c))
      val problems =
        if (foreign.isEmpty) Nil
        else Seq(Problem(ProblemKind.ForeignClass, displayPath, source, parsed.firstLine.getOrElse(foreign.head, 1),
          s"library effect table entries ignored: ${foreign.mkString(", ")} " +
            (if (foreign.size == 1) "is" else "are") + " not in this jar, and a library may classify only its own classes"))
      val own = if (foreign.isEmpty) parsed else parsed.restrictTo(inJar)
      JarResult(Some(own).filter(_.classes.nonEmpty), problems)
    } finally zip.close()
  }
}
