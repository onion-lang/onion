package onion.tools.project

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE

import scala.jdk.CollectionConverters.*
import scala.util.Using
import scala.util.control.NonFatal

private[project] trait PathMover:
  def move(source: Path, target: Path): Unit

private[project] object PathMover:
  val system: PathMover = new PathMover:
    override def move(source: Path, target: Path): Unit =
      try Files.move(source, target, ATOMIC_MOVE)
      catch
        case _: AtomicMoveNotSupportedException =>
          Files.move(source, target)

private[project] object BuildOutputTransaction:
  def promote(
    paths: ProjectPaths,
    stagingRoot: Path,
    mover: PathMover
  ): Either[ProjectError, Unit] =
    validate(paths, stagingRoot).flatMap { validated =>
      promoteValidated(validated, mover)
    }

  private final case class ValidatedPaths(
    target: Path,
    onionState: Path,
    classes: Path,
    buildState: Path,
    stagingRoot: Path,
    stagedClasses: Path,
    stagedState: Path
  )

  private def validate(
    paths: ProjectPaths,
    stagingRoot: Path
  ): Either[ProjectError, ValidatedPaths] =
    try
      val target = requireCanonicalDirectory(paths.target, "project target")
      val onionState = requireCanonicalDirectory(paths.onionState, "project state directory")
      val classes = paths.classes.toAbsolutePath.normalize
      val buildState = paths.buildState.toAbsolutePath.normalize
      val staging = requireCanonicalDirectory(stagingRoot, "build staging directory")
      val stagedClasses = staging.resolve("classes")
      val stagedState = staging.resolve("build-state.json")

      if onionState.getParent != target ||
        onionState.getFileName.toString != ".onion"
      then
        Left(ProjectError(s"Project state directory is outside the canonical target: $onionState"))
      else if classes != target.resolve("classes") then
        Left(ProjectError(s"Project classes path is outside the canonical target: $classes"))
      else if buildState != onionState.resolve("build-state.json") then
        Left(ProjectError(s"Project build-state path is outside the canonical state directory: $buildState"))
      else if staging.getParent != onionState ||
        !staging.getFileName.toString.startsWith("staging-")
      then
        Left(ProjectError(s"Build staging directory is outside the canonical state directory: $staging"))
      else if Files.isSymbolicLink(stagedClasses) ||
        !Files.isDirectory(stagedClasses, NOFOLLOW_LINKS)
      then
        Left(ProjectError(s"Staged classes are not a real directory: $stagedClasses"))
      else if Files.isSymbolicLink(stagedState) ||
        !Files.isRegularFile(stagedState, NOFOLLOW_LINKS)
      then
        Left(ProjectError(s"Staged build state is not a real file: $stagedState"))
      else
        Right(ValidatedPaths(
          target,
          onionState,
          classes,
          buildState,
          staging,
          stagedClasses,
          stagedState
        ))
    catch
      case NonFatal(error) =>
        Left(ProjectError(
          s"Could not validate build output paths: ${describe(error)}",
          Some(error)
        ))

  private def promoteValidated(
    validated: ValidatedPaths,
    mover: PathMover
  ): Either[ProjectError, Unit] =
    val oldClassesExisted = Files.exists(validated.classes, NOFOLLOW_LINKS)
    val oldStateExisted = Files.exists(validated.buildState, NOFOLLOW_LINKS)
    var backupRoot: Option[Path] = None
    var primary: Option[Throwable] = None

    try
      val created =
        Files.createTempDirectory(validated.onionState, "backup-").toRealPath()
      backupRoot = Some(created)
      val backupClasses = created.resolve("classes")
      val backupState = created.resolve("build-state.json")

      if oldClassesExisted then mover.move(validated.classes, backupClasses)
      if oldStateExisted then mover.move(validated.buildState, backupState)
      mover.move(validated.stagedClasses, validated.classes)
      mover.move(validated.stagedState, validated.buildState)
    catch
      case NonFatal(error) =>
        primary = Some(error)

    primary match
      case Some(error) =>
        backupRoot.foreach(root =>
          rollback(
            validated,
            root,
            oldClassesExisted,
            oldStateExisted,
            mover,
            error
          )
        )
        cleanup(validated.stagingRoot, validated.target).foreach(error.addSuppressed)
        backupRoot.foreach(root =>
          cleanupEmptyBackup(root, validated.target).foreach(error.addSuppressed)
        )
        Left(ProjectError(
          s"Could not promote build output: ${describe(error)}",
          Some(error)
        ))
      case None =>
        val cleanupFailures =
          backupRoot.toVector.flatMap(cleanup(_, validated.target)) ++
            cleanup(validated.stagingRoot, validated.target)
        cleanupFailures.headOption match
          case None => Right(())
          case Some(error) =>
            cleanupFailures.drop(1).foreach(error.addSuppressed)
            Left(ProjectError(
              s"Could not clean promoted build output: ${describe(error)}",
              Some(error)
            ))

  private def rollback(
    validated: ValidatedPaths,
    backupRoot: Path,
    oldClassesExisted: Boolean,
    oldStateExisted: Boolean,
    mover: PathMover,
    primary: Throwable
  ): Unit =
    restore(
      validated.buildState,
      backupRoot.resolve("build-state.json"),
      oldStateExisted,
      isTree = false,
      validated.target,
      mover
    ).foreach(primary.addSuppressed)
    restore(
      validated.classes,
      backupRoot.resolve("classes"),
      oldClassesExisted,
      isTree = true,
      validated.target,
      mover
    ).foreach(primary.addSuppressed)

  private def restore(
    destination: Path,
    backup: Path,
    previouslyExisted: Boolean,
    isTree: Boolean,
    trustedBoundary: Path,
    mover: PathMover
  ): Option[Throwable] =
    try
      if previouslyExisted then
        if Files.exists(backup, NOFOLLOW_LINKS) then
          deleteArtifact(destination, isTree, trustedBoundary)
          mover.move(backup, destination)
        else if !Files.exists(destination, NOFOLLOW_LINKS) then
          throw IOException(s"Prior build output is missing from both final and backup paths: $destination")
      else
        deleteArtifact(destination, isTree, trustedBoundary)
      None
    catch
      case NonFatal(error) => Some(error)

  private def deleteArtifact(
    path: Path,
    isTree: Boolean,
    trustedBoundary: Path
  ): Unit =
    if isTree then FileTree.delete(path, trustedBoundary)
    else Files.deleteIfExists(path)

  private def cleanup(path: Path, trustedBoundary: Path): Vector[Throwable] =
    try
      FileTree.delete(path, trustedBoundary)
      Vector.empty
    catch
      case NonFatal(error) => Vector(error)

  private def cleanupEmptyBackup(
    backup: Path,
    trustedBoundary: Path
  ): Vector[Throwable] =
    try
      if !Files.exists(backup, NOFOLLOW_LINKS) then Vector.empty
      else
        val empty = Using.resource(Files.list(backup))(_.iterator.asScala.isEmpty)
        if empty then cleanup(backup, trustedBoundary)
        else Vector.empty
    catch
      case NonFatal(error) => Vector(error)

  private def requireCanonicalDirectory(path: Path, description: String): Path =
    val normalized = path.toAbsolutePath.normalize
    if Files.isSymbolicLink(normalized) ||
      !Files.isDirectory(normalized, NOFOLLOW_LINKS)
    then
      throw IOException(s"$description is not a real directory: $normalized")
    val canonical = normalized.toRealPath()
    if canonical != normalized then
      throw IOException(s"$description is not canonical: $normalized")
    canonical

  private def describe(error: Throwable): String =
    Option(error.getMessage).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)
