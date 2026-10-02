package onion.tools.project

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, StandardCopyOption}
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.{ArrayList, LinkedHashMap}

import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

import onion.Json

/**
 * A machine-local record of the classpath a locked resolution produced, so the next build
 * can reuse it instead of asking coursier the same question again.
 *
 * Every `onion build`/`run`/`test` of a project with dependencies used to resolve, even
 * when `onion.lock` matched the manifest and every jar was already in the local cache:
 * about a second per command on a POI project, against a third of one without
 * dependencies. With a matching lock the answer is already known — the lock says which
 * artifacts, and this record says where they are on this machine.
 *
 * It lives in `target/.onion/` beside the build state, because it is neither portable nor
 * worth committing: it holds absolute paths into this machine's coursier cache.
 *
 * '''When the record is trusted.''' It is keyed by [[DependencyLock.Locked.key]], a digest
 * of the lock's contents, so an edited or replaced lock never reuses a classpath recorded
 * for a different one. It is used only if every recorded jar is still a regular file, and
 * the set of (file name, SHA-256) it records equals the lock's artifact set exactly. A
 * jar's recorded SHA-256 is re-used only while its size and modification time are the
 * ones recorded; if either moved, the jar is hashed again and must still agree with the
 * lock. Hashing every jar on every command would cost a noticeable share of what the fast
 * path saves (POI's thirteen jars are ~20 MB), and a same-size, same-mtime rewrite of a
 * file inside coursier's cache is not a failure the resolving path would catch either —
 * coursier does not re-verify a cached file before handing it back.
 *
 * Anything else — no record, an unreadable one, a different key, a missing or changed
 * jar — is a miss, and the build resolves as it would have without this file. A record
 * is a cache: it can only make a build faster, never make one succeed that would fail.
 */
object DependencyClasspathRecord:

  val FileName = "dependency-classpath.json"

  private val SchemaVersion = "1"

  def path(paths: ProjectPaths): Path = paths.onionState.resolve(FileName)

  private final case class Entry(path: Path, size: Long, modified: Long, sha256: String)

  /**
   * The classpath recorded for exactly this lock, if it is still what the lock says;
   * `None` means resolve.
   */
  def reuse(file: Path, locked: DependencyLock.Locked): Option[ResolvedDependencies] =
    try
      if Files.isSymbolicLink(file) || !Files.isRegularFile(file, NOFOLLOW_LINKS) then None
      else decode(Files.readString(file, UTF_8), locked.key).flatMap { entries =>
        val current = entries.map(refresh)
        if current.exists(_.isEmpty) then None
        else
          val found = current.flatten
          val artifacts = found.map(e =>
            DependencyLock.LockedArtifact(e.path.getFileName.toString, e.sha256)).toSet
          // The file count guards a name+hash pair that appears twice collapsing into one.
          if artifacts == locked.artifacts && found.size == locked.artifacts.size then
            Some(ResolvedDependencies(locked.coordinates, found.map(_.path)))
          else None
      }
    catch case NonFatal(_) => None

  /**
   * Records `resolved` as the classpath for `locked`. Written to a sibling and moved into
   * place, so a concurrent build reads the old record or the new one, never half of one.
   */
  def write(
    file: Path,
    locked: DependencyLock.Locked,
    resolved: ResolvedDependencies
  ): Either[ProjectError, Unit] =
    var temporary: Option[Path] = None
    try
      val entries = resolved.classpath.map { jar =>
        val absolute = jar.toAbsolutePath.normalize
        Entry(absolute, Files.size(absolute), modifiedOf(absolute), DependencyLock.sha256(absolute))
      }
      val created = Files.createTempFile(file.getParent, "dependency-classpath-", ".tmp")
      temporary = Some(created)
      Files.writeString(created, encode(locked.key, entries), UTF_8)
      try Files.move(created, file, StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING)
      catch case _: AtomicMoveNotSupportedException =>
        Files.move(created, file, StandardCopyOption.REPLACE_EXISTING)
      temporary = None
      Right(())
    catch
      case NonFatal(error) =>
        Left(ProjectError(s"Could not record the dependency classpath: ${error.getMessage}",
          Some(error)))
    finally
      temporary.foreach(t => try Files.deleteIfExists(t) catch case NonFatal(_) => ())

  /** The entry as it is on disk now, re-hashed only when its size or mtime moved. */
  private def refresh(entry: Entry): Option[Entry] =
    val jar = entry.path
    if Files.isSymbolicLink(jar) || !Files.isRegularFile(jar, NOFOLLOW_LINKS) then None
    else
      val size = Files.size(jar)
      val modified = modifiedOf(jar)
      if size == entry.size && modified == entry.modified then Some(entry)
      else Some(Entry(jar, size, modified, DependencyLock.sha256(jar)))

  private def modifiedOf(file: Path): Long =
    Files.getLastModifiedTime(file, NOFOLLOW_LINKS).toMillis

  // ------------------------------------------------------------------ format

  private def encode(key: String, entries: Seq[Entry]): String =
    val root = LinkedHashMap[String, Object]()
    root.put("schemaVersion", SchemaVersion)
    root.put("lock", key)
    val jars = ArrayList[Object]()
    entries.foreach { entry =>
      val encoded = LinkedHashMap[String, Object]()
      encoded.put("path", entry.path.toString)
      // As strings: a JSON number is a double to some readers, and an mtime in
      // milliseconds is not a value to round.
      encoded.put("size", entry.size.toString)
      encoded.put("modified", entry.modified.toString)
      encoded.put("sha256", entry.sha256)
      jars.add(encoded)
    }
    root.put("classpath", jars)
    Json.stringifyPretty(root)

  private def decode(text: String, expectedKey: String): Option[Seq[Entry]] =
    val root = Json.asObject(Json.parse(text))
    if root == null ||
      root.get("schemaVersion") != SchemaVersion ||
      root.get("lock") != expectedKey
    then None
    else
      Option(Json.asArray(root.get("classpath"))).flatMap { jars =>
        val entries = jars.asScala.toSeq.map { value =>
          val jar = Json.asObject(value)
          if jar == null then None
          else
            (jar.get("path"), jar.get("size"), jar.get("modified"), jar.get("sha256")) match
              case (path: String, size: String, modified: String, sha256: String) =>
                for
                  s <- size.toLongOption
                  m <- modified.toLongOption
                  p = Path.of(path)
                  if p.isAbsolute
                yield Entry(p, s, m, sha256)
              case _ => None
        }
        if entries.exists(_.isEmpty) then None else Some(entries.flatten)
      }
