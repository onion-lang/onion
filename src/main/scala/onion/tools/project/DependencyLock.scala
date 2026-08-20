package onion.tools.project

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.security.MessageDigest

import scala.util.Using

import org.tomlj.{Toml, TomlArray, TomlTable}

/**
 * `onion.lock` — the exact artifacts a build resolved, so the next build gets the same ones.
 *
 * `[dependencies]` names direct dependencies at a version each, and that is not enough to
 * reproduce a build. A transitive dependency's version is chosen at resolution time, so two
 * builds of a byte-identical `onion.toml` can compile against different jars — a new
 * transitive release is all it takes. The failure that produces is the worst kind: it
 * appears on one machine, cannot be reproduced on another, and nothing in the project
 * changed.
 *
 * The lock records two things, and deliberately keeps them apart:
 *
 *   - the '''whole transitive coordinate set''', which a later build resolves ''instead of''
 *     re-deriving it, so every version is pinned including the ones nobody wrote down;
 *   - a SHA-256 per artifact '''file''', compared as a set before anything is compiled.
 *
 * They are separate because coursier's result exposes resolved coordinates and downloaded
 * files as two lists with no stated correspondence — the coordinates arrive sorted and the
 * files in resolution order. Zipping them looks obvious, produces a plausible-looking lock
 * file, and pairs the wrong hash with the wrong coordinate; the mismatch then surfaces as a
 * spurious integrity failure on a perfectly good build. Comparing sets needs no
 * correspondence at all, and tolerates two artifacts sharing a file name.
 *
 * A hash mismatch is a hard failure rather than a warning. The bytes behind a published
 * version are supposed to be immutable, and a repository that serves different ones is
 * either broken or hostile — neither is something to compile against.
 *
 * The lock is keyed to the manifest input that produced it. Change a version in
 * `onion.toml`, or add a repository, and the lock no longer applies: it is discarded and
 * rewritten rather than half-honoured, because a lock that answers a different question is
 * not an answer.
 *
 * '''This is not an offline mode.''' coursier's embedding API (`coursierapi.Cache`) exposes
 * a location, a thread pool and a logger, and no cache policy whatsoever, so there is no
 * honest way to promise a build that never reaches the network. What the lock gives is the
 * same answer every time, not the absence of the question.
 */
object DependencyLock:

  val FileName = "onion.lock"

  /** The format version, so a future change is detected rather than misread. */
  private val SchemaVersion = 1

  /**
   * @param dependencies the direct dependencies this lock was resolved from, sorted
   * @param repositories the repositories in the order they were searched
   * @param coordinates  the whole transitive set, sorted — what a later build resolves
   * @param artifacts    a SHA-256 per artifact file name, compared as a set
   */
  final case class Locked(
    dependencies: Seq[String],
    repositories: Seq[String],
    coordinates: Seq[String],
    artifacts: Set[LockedArtifact]
  ):
    /** Whether this lock answers the question the manifest is asking. */
    def matches(manifest: ProjectManifest): Boolean =
      dependencies == inputOf(manifest) && repositories == manifest.repositories

  final case class LockedArtifact(file: String, sha256: String)

  def path(paths: ProjectPaths): Path = paths.root.resolve(FileName)

  private def inputOf(manifest: ProjectManifest): Seq[String] =
    manifest.dependencies.map(d => s"${d.group}:${d.artifact}:${d.version}").sorted

  // ------------------------------------------------------------------ reading

  /**
   * The lock, if there is one that can be read. A lock that is missing, unreadable, or
   * written by a newer Onion is simply absent, and the build re-resolves and rewrites it —
   * which is what anyone wants from a generated file. A lock that is '''present and
   * disagrees''' is a different matter, and that is [[verify]]'s job.
   */
  def read(paths: ProjectPaths): Option[Locked] = read(path(paths))

  private[project] def read(file: Path): Option[Locked] =
    if !Files.isRegularFile(file) then None
    else
      try
        val toml = Toml.parse(Files.readString(file, UTF_8))
        // A lock written by a newer Onion is not read, rather than read wrongly.
        val schema = Option(toml.getLong("version")).map(_.longValue).getOrElse(0L)
        if toml.hasErrors || schema != SchemaVersion then None
        else
          Some(Locked(
            dependencies = strings(toml.getArray("input.dependencies")),
            repositories = strings(toml.getArray("input.repositories")),
            coordinates = strings(toml.getArray("input.coordinates")),
            artifacts = readArtifacts(toml.getArray("artifact"))))
      catch case _: IOException => None

  private def readArtifacts(array: TomlArray): Set[LockedArtifact] =
    if array == null then Set.empty
    else
      (0 until array.size()).flatMap { index =>
        array.get(index) match
          case entry: TomlTable =>
            for
              file <- Option(entry.getString("file"))
              sha256 <- Option(entry.getString("sha256"))
            yield LockedArtifact(file, sha256)
          case _ => None
      }.toSet

  private def strings(array: TomlArray): Seq[String] =
    if array == null then Seq.empty
    else (0 until array.size()).flatMap(index => Option(array.getString(index))).toSeq

  // ------------------------------------------------------------------ writing

  def write(
    paths: ProjectPaths,
    manifest: ProjectManifest,
    resolved: ResolvedDependencies
  ): Either[ProjectError, Unit] =
    write(
      path(paths),
      Locked(
        dependencies = inputOf(manifest),
        repositories = manifest.repositories,
        coordinates = resolved.coordinates,
        artifacts = fingerprint(resolved)))

  private[project] def write(file: Path, locked: Locked): Either[ProjectError, Unit] =
    val out = new StringBuilder
    out ++= "# Generated by onion. Commit this file: it is what makes a build reproducible.\n"
    out ++= "# Delete it to resolve from scratch. Editing it by hand only invites a checksum\n"
    out ++= "# mismatch on the next build.\n"
    out ++= s"version = $SchemaVersion\n\n"
    out ++= "[input]\n"
    out ++= s"dependencies = ${array(locked.dependencies)}\n"
    out ++= s"repositories = ${array(locked.repositories)}\n"
    out ++= s"coordinates = ${array(locked.coordinates)}\n"
    locked.artifacts.toSeq.sortBy(a => (a.file, a.sha256)).foreach { entry =>
      out ++= "\n[[artifact]]\n"
      out ++= s"file = ${quote(entry.file)}\n"
      out ++= s"sha256 = ${quote(entry.sha256)}\n"
    }
    try
      Files.writeString(file, out.toString, UTF_8)
      Right(())
    catch
      case error: IOException =>
        Left(ProjectError(s"Could not write $file: ${error.getMessage}", Some(error)))

  private def array(values: Seq[String]): String = values.map(quote).mkString("[", ", ", "]")

  private def quote(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  // ------------------------------------------------------------------ verifying

  /** Compares what was just resolved against what the lock recorded. */
  def verify(locked: Locked, resolved: ResolvedDependencies): Either[ProjectError, Unit] =
    val found = fingerprint(resolved)
    if found == locked.artifacts then Right(())
    else
      val lockedByFile = locked.artifacts.map(a => a.file -> a.sha256).toMap
      val changed = found.toSeq.sortBy(_.file).collect {
        case artifact if lockedByFile.get(artifact.file).exists(_ != artifact.sha256) =>
          s"  ${artifact.file}\n    locked ${lockedByFile(artifact.file)}\n    found  ${artifact.sha256}"
      }
      val appeared = (found.map(_.file) -- lockedByFile.keySet).toSeq.sorted
      val vanished = (lockedByFile.keySet -- found.map(_.file)).toSeq.sorted

      val report = new StringBuilder(s"Resolved dependencies do not match $FileName:\n")
      if changed.nonEmpty then
        report ++= s"different bytes for the same file:\n${changed.mkString("\n")}\n"
      if appeared.nonEmpty then report ++= s"not in the lock: ${appeared.mkString(", ")}\n"
      if vanished.nonEmpty then report ++= s"missing: ${vanished.mkString(", ")}\n"
      report ++= "A published version's bytes should never change. Check the repository, or " +
        s"delete $FileName to accept what it is serving now."
      Left(ProjectError(report.toString))

  /** A SHA-256 per artifact file — a set, because there is no order to rely on. */
  private def fingerprint(resolved: ResolvedDependencies): Set[LockedArtifact] =
    resolved.classpath.map(file => LockedArtifact(file.getFileName.toString, sha256(file))).toSet

  private[project] def sha256(file: Path): String =
    try
      val digest = MessageDigest.getInstance("SHA-256")
      Using.resource(Files.newInputStream(file)) { stream =>
        val buffer = new Array[Byte](64 * 1024)
        var read = stream.read(buffer)
        while read > 0 do
          digest.update(buffer, 0, read)
          read = stream.read(buffer)
      }
      digest.digest().map(byte => f"${byte & 0xff}%02x").mkString
    catch
      // A jar that cannot be read fails the build a moment later with a better message than
      // anything here could produce. An unreadable file is not a checksum mismatch.
      case _: IOException => "unreadable"
