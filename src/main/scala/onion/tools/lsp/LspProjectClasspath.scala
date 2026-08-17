package onion.tools.lsp

import java.nio.file.{Files, Path}
import java.util.concurrent.ConcurrentHashMap

import onion.tools.project.{DependencyResolver, ProjectLocator, ProjectManifest}

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
 */
object LspProjectClasspath {

  private final case class Stamp(size: Long, modified: Long)
  private final case class Entry(stamp: Stamp, classpath: Seq[String])

  private val cache = new ConcurrentHashMap[Path, Entry]()

  /** What the fixed configuration used before any of this existed. */
  private val standalone: Seq[String] = Seq(".")

  /**
   * @param file the document being validated, or None when the client gave a URI that is
   *             not a file (an untitled buffer, say), in which case there is no project to
   *             find and the standalone classpath is the honest answer.
   */
  def forDocument(file: Option[Path]): Seq[String] =
    file.flatMap(project).getOrElse(standalone)

  private def project(file: Path): Option[Seq[String]] =
    val start = if Files.isDirectory(file) then file else file.getParent
    if start == null then None
    else
      ProjectLocator.locate(start).toOption.flatMap { paths =>
        stampOf(paths.manifest).map { stamp =>
          val cached = cache.get(paths.root)
          if cached != null && cached.stamp == stamp then cached.classpath
          else
            val computed = compute(paths.root, paths.manifest, paths.classes)
            cache.put(paths.root, Entry(stamp, computed))
            computed
        }
      }

  private def compute(root: Path, manifestPath: Path, classes: Path): Seq[String] =
    // The project's own build output comes first so that a symbol from a sibling file
    // resolves. It only exists after a build; before that this is simply a path that is
    // not there, which the compiler already tolerates.
    val own = Seq(classes.toString)
    ProjectManifest.load(manifestPath) match
      case Left(error) =>
        // A malformed manifest is the build's problem to report, not a reason to stop
        // validating the file the author is looking at.
        warn(s"$manifestPath: ${error.message}")
        own
      case Right(manifest) =>
        DependencyResolver.resolve(manifest.dependencies, manifest.repositories) match
          case Left(error) =>
            // Degrading here is deliberate: an editor that refuses to validate because a
            // jar cannot be fetched is worse than one that validates without it. Say so
            // rather than letting the resulting phantom errors look like the user's fault.
            warn(s"$root: ${error.message}; validating without dependencies")
            own
          case Right(resolved) =>
            own ++ resolved.classpath.map(_.toString)

  private def warn(message: String): Unit =
    // The server's stderr is surfaced by the client as the language server's output
    // channel, which is where a user goes to find out why the editor disagrees with the
    // build; stdout is the LSP wire protocol and must not be written to.
    System.err.println(s"[onion-lsp] $message")

  /** Cleared between tests, and whenever a client reconnects to a fresh workspace. */
  private[lsp] def invalidate(): Unit = cache.clear()

  private def stampOf(manifest: Path): Option[Stamp] =
    try
      if !Files.isRegularFile(manifest) then None
      else Some(Stamp(Files.size(manifest), Files.getLastModifiedTime(manifest).toMillis))
    catch case _: java.io.IOException => None
}
