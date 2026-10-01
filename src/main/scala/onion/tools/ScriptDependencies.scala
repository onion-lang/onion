package onion.tools

import java.io.PrintStream
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.security.MessageDigest

import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

import onion.tools.project.DependencyResolver

/**
 * Turns a script's `//> using` directives (see [[ScriptDirectives]]) into classpath entries,
 * through the same [[DependencyResolver]] a project uses.
 *
 * A script has no lock file: the directives pin each direct dependency to an exact version,
 * but transitive versions are whatever resolution picks. A project (`onion.toml` plus
 * `onion.lock`) is the reproducible form.
 *
 * Resolution through coursier costs about a second even when every jar is already in its
 * cache, which is most of a small script's run time. So the resolved classpath is cached per
 * directive set, keyed by a hash of the sorted dependencies and the ordered repositories, in
 * `<cache dir>/script-deps/<hash>.classpath`, and reused as long as every jar it names still
 * exists. A cache that cannot be read or written only costs that second; it never fails a run.
 */
object ScriptDependencies {

  /** Bumped when the cache file's format or the key's inputs change. */
  private val CacheFormat = "onion-script-classpath-v1"

  /**
   * The classpath entries a script's directives add, or the error to print (already rendered,
   * naming the script and the line). A script without directives, or one that cannot be read
   * (the compiler will report that), adds nothing.
   */
  def classpathFor(script: String, encoding: String, progress: PrintStream): Either[String, Seq[String]] =
    classpathForSources(Seq(script), encoding, progress)

  /**
   * The classpath entries the directives of several sources compiled together add (what
   * `onionc a.on b.on` uses): the union of their directives (see [[ScriptDirectives.parseAll]];
   * two versions of one module across files is an error, as it is within one), resolved and
   * cached exactly as a single script's are. Files that cannot be read add nothing.
   */
  def classpathForSources(files: Seq[String], encoding: String, progress: PrintStream): Either[String, Seq[String]] = {
    val sources = files.flatMap { file =>
      try Some(file -> new String(Files.readAllBytes(Paths.get(file)), charset(encoding)))
      catch { case NonFatal(_) => None }
    }
    ScriptDirectives.parseAll(sources) match {
      case Left(error) => Left(error)
      case Right(directives) if directives.isEmpty => Right(Seq.empty)
      case Right(directives) =>
        resolve(directives, cacheDirectory(), progress).left.map { message =>
          // Name the files that asked for something: they are where the fix goes.
          val declaring = sources.collect {
            case (file, text) if ScriptDirectives.parse(text).exists(!_.isEmpty) => file
          }
          s"${declaring.mkString(", ")}: error: $message"
        }
    }
  }

  /** Resolves `directives`, through the cache under `cacheDir` when there is one. */
  def resolve(
    directives: ScriptDirectives.Directives,
    cacheDir: Option[Path],
    progress: PrintStream
  ): Either[String, Seq[String]] =
    resolve(directives, cacheDir, Some(progress))

  /**
   * [[resolve]], drawing download progress only when `progress` is given: the language
   * server passes None, because its stdout is the protocol and its stderr is a log.
   */
  def resolve(
    directives: ScriptDirectives.Directives,
    cacheDir: Option[Path],
    progress: Option[PrintStream]
  ): Either[String, Seq[String]] = {
    val cacheFile = cacheFileFor(directives, cacheDir)
    cacheFile.flatMap(readCache) match {
      case Some(hit) => Right(hit)
      case None =>
        DependencyResolver.resolve(directives.dependencies, directives.repositories, progress) match {
          case Left(error) => Left(error.message)
          case Right(resolved) =>
            val jars = resolved.classpath.map(_.toAbsolutePath.toString)
            cacheFile.foreach(writeCache(_, jars))
            Right(jars)
        }
    }
  }

  /**
   * The cached classpath for `directives`, without resolving: None when there is no usable
   * entry. Reads one small file and checks that its jars exist, so a caller that must not
   * block on the network (the language server) can ask before deciding to resolve.
   */
  def cached(directives: ScriptDirectives.Directives, cacheDir: Option[Path]): Option[Seq[String]] =
    cacheFileFor(directives, cacheDir).flatMap(readCache)

  private def cacheFileFor(directives: ScriptDirectives.Directives, cacheDir: Option[Path]): Option[Path] =
    cacheDir.map(_.resolve("script-deps").resolve(cacheKey(directives) + ".classpath"))

  /**
   * SHA-256 over the format tag, the dependencies sorted by coordinate (declaration order does
   * not change what resolves) and the repositories in declaration order (which does: it is
   * precedence).
   */
  private[tools] def cacheKey(directives: ScriptDirectives.Directives): String = {
    val text = (Seq(CacheFormat, "deps") ++ directives.dependencies.map(_.render).sorted ++
      Seq("repositories") ++ directives.repositories).mkString("\n")
    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))
      .map(b => f"${b & 0xff}%02x").mkString
  }

  /** The cached jars, when the file is well-formed and every jar is still there. */
  private def readCache(file: Path): Option[Seq[String]] =
    try {
      if (!Files.isRegularFile(file)) None
      else {
        val lines = Files.readAllLines(file, StandardCharsets.UTF_8).asScala.toSeq
        lines match {
          case header +: jars if header == CacheFormat && jars.nonEmpty &&
              jars.forall(jar => jar.nonEmpty && Files.isRegularFile(Paths.get(jar))) =>
            Some(jars)
          case _ => None
        }
      }
    } catch { case NonFatal(_) => None }

  /** Written to a temporary file and moved into place, so a reader never sees half a file. */
  private def writeCache(file: Path, jars: Seq[String]): Unit =
    try {
      Files.createDirectories(file.getParent)
      val temp = Files.createTempFile(file.getParent, file.getFileName.toString, ".tmp")
      try {
        Files.write(temp, (CacheFormat +: jars).asJava, StandardCharsets.UTF_8)
        try Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        catch {
          case _: java.nio.file.AtomicMoveNotSupportedException =>
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
      } finally Files.deleteIfExists(temp)
    } catch { case NonFatal(_) => () }

  /**
   * Where Onion keeps caches: `-Donion.cache.dir`, else `$ONION_CACHE_DIR`, else the
   * platform's user cache directory (`%LOCALAPPDATA%\onion\cache` on Windows,
   * `~/Library/Caches/onion` on macOS, `$XDG_CACHE_HOME/onion` or `~/.cache/onion` elsewhere).
   */
  def cacheDirectory(): Option[Path] =
    try {
      def nonEmpty(value: String): Option[String] = Option(value).map(_.trim).filter(_.nonEmpty)
      val home = nonEmpty(System.getProperty("user.home"))
      val os = System.getProperty("os.name", "").toLowerCase
      nonEmpty(System.getProperty("onion.cache.dir"))
        .orElse(nonEmpty(System.getenv("ONION_CACHE_DIR")))
        .map(Paths.get(_).toAbsolutePath)
        .orElse {
          if (os.contains("win"))
            nonEmpty(System.getenv("LOCALAPPDATA")).map(Paths.get(_, "onion", "cache"))
              .orElse(home.map(Paths.get(_, "AppData", "Local", "onion", "cache")))
          else if (os.contains("mac")) home.map(Paths.get(_, "Library", "Caches", "onion"))
          else nonEmpty(System.getenv("XDG_CACHE_HOME")).map(Paths.get(_, "onion"))
            .orElse(home.map(Paths.get(_, ".cache", "onion")))
        }
    } catch { case NonFatal(_) => None }

  private def charset(name: String): Charset =
    try Charset.forName(name) catch { case NonFatal(_) => StandardCharsets.UTF_8 }
}
