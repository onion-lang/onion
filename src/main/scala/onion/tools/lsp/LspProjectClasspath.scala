package onion.tools.lsp

import java.nio.file.{Files, Path}
import java.util.concurrent.{ConcurrentHashMap, ExecutorService, Executors, ThreadFactory}

import scala.util.control.NonFatal

import onion.tools.project.{DependencyClasspathRecord, DependencyLock, DependencyResolver, ProjectLocator, ProjectManifest, ProjectPaths}

/**
 * The classpath the editor should validate a document against.
 *
 * Validation used to compile every document with a fixed classpath of `.`, which was
 * already wrong for a project — nothing in `target/classes` was visible, so a symbol
 * defined in a sibling file read as undefined — and became actively misleading once
 * `onion.toml` grew a `[dependencies]` table: the build would compile happily while the
 * editor underlined every type that came from a jar.
 *
 * Resolution is cached per project root and invalidated by the manifest's size and
 * modification time. Resolving on every keystroke would be unusable even with a warm
 * coursier cache, and the manifest is the only input that can change the answer.
 *
 * [[lookup]] never resolves on the caller's thread: it answers from the cache or from the
 * classpath the last build recorded, and hands a colder miss (the first request after a
 * manifest or lock change) to one background thread, as [[LspScriptClasspath]] does for a
 * standalone script. [[forDocument]] is the blocking form, for callers that have no
 * revalidation to wait for.
 */
object LspProjectClasspath {

  private final case class Stamp(size: Long, modified: Long)
  private final case class Entry(stamp: Stamp, classpath: Seq[String])

  private val cache = new ConcurrentHashMap[Path, Entry]()

  /** The manifest stamp whose resolution is running in the background, per project root. */
  private val pending = new ConcurrentHashMap[Path, Stamp]()

  private lazy val executor: ExecutorService =
    Executors.newSingleThreadExecutor(new ThreadFactory:
      override def newThread(task: Runnable): Thread =
        val thread = new Thread(task, "onion-lsp-project-dependencies")
        thread.setDaemon(true)
        thread
    )

  /** The answer to [[lookup]]. */
  enum Lookup:
    /** The classpath to validate against. */
    case Ready(classpath: Seq[String])
    /** Resolution is running in the background; `onSettled` follows with the project root. */
    case Pending

  /** What the fixed configuration used before any of this existed. */
  private val standalone: Seq[String] = Seq(".")

  /**
   * @param file the document being validated, or None when the client gave a URI that is
   *             not a file (an untitled buffer, say), in which case there is no project to
   *             find and the standalone classpath is the honest answer.
   */
  def forDocument(file: Option[Path]): Seq[String] =
    file.flatMap(project).getOrElse(standalone)

  /**
   * Like [[forDocument]] but never blocks on dependency resolution.
   *
   * @param onSettled called on the background thread, with the project root, once a
   *                  [[Lookup.Pending]] answer has been replaced by a cached one
   */
  def lookup(file: Option[Path], onSettled: Path => Unit): Lookup =
    file.flatMap(locate) match
      case None => Lookup.Ready(standalone)
      case Some(paths) =>
        stampOf(paths.manifest) match
          case None => Lookup.Ready(standalone)
          case Some(stamp) =>
            val cached = cache.get(paths.root)
            if cached != null && cached.stamp == stamp then Lookup.Ready(cached.classpath)
            else
              compute(paths, resolveNow = false) match
                case Some(classpath) =>
                  cache.put(paths.root, Entry(stamp, classpath))
                  Lookup.Ready(classpath)
                case None =>
                  if pending.get(paths.root) != stamp then
                    pending.put(paths.root, stamp)
                    executor.execute(() => settle(paths, stamp, onSettled))
                  Lookup.Pending

  private def settle(paths: ProjectPaths, stamp: Stamp, onSettled: Path => Unit): Unit =
    try
      cache.put(paths.root, Entry(stamp, compute(paths, resolveNow = true).getOrElse(Seq(paths.classes.toString))))
    catch case NonFatal(e) =>
      warn(s"${paths.root}: ${e.getClass.getSimpleName}: ${e.getMessage}; validating without dependencies")
      cache.put(paths.root, Entry(stamp, Seq(paths.classes.toString)))
    finally pending.remove(paths.root, stamp)
    try onSettled(paths.root)
    catch case NonFatal(e) => warn(s"revalidation after dependency resolution failed: $e")

  /**
   * The root of the project (the directory holding `onion.toml`) the document belongs to,
   * or None for a standalone file. A file with a manifest above it validates as `onion
   * build` compiles it, from the manifest; one without is a script.
   */
  def projectRoot(file: Path): Option[Path] = locate(file).map(_.root)

  private def locate(file: Path): Option[ProjectPaths] =
    val start = if Files.isDirectory(file) then file else file.getParent
    if start == null then None else ProjectLocator.locate(start).toOption

  private def project(file: Path): Option[Seq[String]] =
    locate(file).flatMap { paths =>
      stampOf(paths.manifest).map { stamp =>
        val cached = cache.get(paths.root)
        if cached != null && cached.stamp == stamp then cached.classpath
        else
          val computed = compute(paths, resolveNow = true).get
          cache.put(paths.root, Entry(stamp, computed))
          computed
      }
    }

  /** None only when `resolveNow` is false and the answer needs the resolver. */
  private def compute(paths: ProjectPaths, resolveNow: Boolean): Option[Seq[String]] =
    val root = paths.root
    val manifestPath = paths.manifest
    val classes = paths.classes
    // The project's own build output comes first so that a symbol from a sibling file
    // resolves. It only exists after a build; before that this is simply a path that is
    // not there, which the compiler already tolerates.
    val own = Seq(classes.toString)
    ProjectManifest.load(manifestPath) match
      case Left(error) =>
        // A malformed manifest is the build's problem to report, not a reason to stop
        // validating the file the author is looking at.
        warn(s"$manifestPath: ${error.message}")
        Some(own)
      case Right(manifest) =>
        // The classpath the last build resolved and verified against `onion.lock`, when
        // the lock still answers this manifest and every jar is still what it records.
        // Read-only: the editor never writes the lock or the record, and anything short
        // of an exact match falls through to resolving as before.
        val recorded = DependencyLock.read(paths).filter(_.matches(manifest)).flatMap(
          DependencyClasspathRecord.reuse(DependencyClasspathRecord.path(paths), _))
        if recorded.isEmpty && manifest.dependencies.nonEmpty && !resolveNow then None
        else Some(recorded.map(Right(_)).getOrElse(
          DependencyResolver.resolve(manifest.dependencies, manifest.repositories)) match
          case Left(error) =>
            // Degrading here is deliberate: an editor that refuses to validate because a
            // jar cannot be fetched is worse than one that validates without it. Say so
            // rather than letting the resulting phantom errors look like the user's fault.
            warn(s"$root: ${error.message}; validating without dependencies")
            own
          case Right(resolved) =>
            own ++ resolved.classpath.map(_.toString))

  private def warn(message: String): Unit =
    // The server's stderr is surfaced by the client as the language server's output
    // channel, which is where a user goes to find out why the editor disagrees with the
    // build; stdout is the LSP wire protocol and must not be written to.
    System.err.println(s"[onion-lsp] $message")

  /** Cleared between tests, and whenever a client reconnects to a fresh workspace. */
  private[lsp] def invalidate(): Unit =
    cache.clear()
    pending.clear()

  private def stampOf(manifest: Path): Option[Stamp] =
    try
      if !Files.isRegularFile(manifest) then None
      else Some(Stamp(Files.size(manifest), Files.getLastModifiedTime(manifest).toMillis))
    catch case _: java.io.IOException => None
}
