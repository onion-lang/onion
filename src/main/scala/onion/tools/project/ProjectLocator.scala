package onion.tools.project

import java.nio.file.Files
import java.nio.file.Path

final case class ProjectPaths(
  root: Path,
  manifest: Path,
  target: Path,
  classes: Path,
  onionState: Path,
  buildState: Path
)

object ProjectLocator:
  private val ManifestName = "onion.toml"

  def locate(start: Path): Either[ProjectError, ProjectPaths] =
    val normalizedStart = start.toAbsolutePath.normalize
    findProjectRoot(normalizedStart) match
      case None => Left(ProjectError(s"Could not find $ManifestName from $normalizedStart"))
      case Some(root) =>
        try
          val canonicalRoot = root.toRealPath()
          val target = canonicalRoot.resolve("target")
          val onionState = target.resolve(".onion")
          Right(ProjectPaths(
            canonicalRoot,
            canonicalRoot.resolve(ManifestName),
            target,
            target.resolve("classes"),
            onionState,
            onionState.resolve("build-state.json")
          ))
        catch
          case error: java.io.IOException => Left(ProjectError(s"Could not resolve project root: ${error.getMessage}", Some(error)))

  private def findProjectRoot(start: Path): Option[Path] =
    var current: Path | Null = start
    while current != null do
      if Files.isRegularFile(current.resolve(ManifestName)) then return Some(current)
      current = current.getParent
    None
