package onion.tools.project

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

import scala.util.control.NonFatal

import onion.compiler.CompilerConfig
import onion.compiler.OnionCompiler
import onion.compiler.StreamInputSource
import onion.compiler.WarningLevel
import onion.compiler.diagnostics.DiagnosticRenderer
import onion.tools.CompiledClassWriter
import onion.tools.OnionVersion

final case class ProjectBuild(
  paths: ProjectPaths,
  manifest: ProjectManifest,
  layout: ProjectLayout,
  state: BuildState,
  cached: Boolean
)

object ProjectBuilder:
  private[project] def requireParsedUnits(
    units: Option[Seq[onion.compiler.AST.CompilationUnit]]
  ): Either[ProjectError, Seq[onion.compiler.AST.CompilationUnit]] =
    units.filter(_.nonEmpty).toRight(ProjectError(
      "Internal project error: successful compilation returned no parsed source units"
    ))

final class ProjectBuilder(
  compilerVersion: String = OnionVersion.value,
  javaFeature: Int = Runtime.version.feature,
  mover: PathMover = PathMover.system
):
  private final case class SourceSnapshot(
    source: ProjectSource,
    bytes: Array[Byte]
  )

  def build(
    paths: ProjectPaths,
    manifest: ProjectManifest,
    layout: ProjectLayout,
    err: PrintStream
  ): Either[ProjectError, ProjectBuild] =
    if layout.productionSources.isEmpty then
      Left(ProjectError("Project has no production sources"))
    else
      for
        _ <- validatePaths(paths)
        sources <- readSources(layout.productionSources)
        fingerprint = BuildFingerprint.compute(
          manifest.bytes,
          sources.map(snapshot => snapshot.source.relative -> snapshot.bytes),
          compilerVersion,
          javaFeature
        )
        result <- cachedBuild(paths, manifest, layout, fingerprint) match
          case Some(hit) => Right(hit)
          case None => compile(paths, manifest, layout, sources, fingerprint, err)
      yield result

  private def cachedBuild(
    paths: ProjectPaths,
    manifest: ProjectManifest,
    layout: ProjectLayout,
    fingerprint: String
  ): Option[ProjectBuild] =
    if Files.isSymbolicLink(paths.buildState) ||
      !Files.isRegularFile(paths.buildState, NOFOLLOW_LINKS)
    then None
    else
      BuildState.load(paths.buildState).toOption
        .filter(_.fingerprint == fingerprint)
        .filter(BuildState.validatesOutputs(_, paths.classes))
        .map(state => ProjectBuild(paths, manifest, layout, state, cached = true))

  private def compile(
    paths: ProjectPaths,
    manifest: ProjectManifest,
    layout: ProjectLayout,
    sources: Vector[SourceSnapshot],
    fingerprint: String,
    err: PrintStream
  ): Either[ProjectError, ProjectBuild] =
    createStaging(paths).flatMap { staging =>
      val result =
        try compileStaged(paths, manifest, layout, sources, fingerprint, staging, err)
        catch
          case NonFatal(error) =>
            Left(ProjectError(
              s"Could not build project: ${describe(error)}",
              Some(error)
            ))
      finishStaging(result, staging, paths.target)
    }

  private def compileStaged(
    paths: ProjectPaths,
    manifest: ProjectManifest,
    layout: ProjectLayout,
    sources: Vector[SourceSnapshot],
    fingerprint: String,
    staging: Path,
    err: PrintStream
  ): Either[ProjectError, ProjectBuild] =
    val stagedClasses = staging.resolve("classes")
    val config = CompilerConfig(
      classPath = Seq.empty,
      superClass = "",
      encoding = UTF_8.name(),
      outputDirectory = stagedClasses.toString,
      maxErrorReports = 10,
      verbose = false,
      warningLevel = WarningLevel.On,
      checkLaws = true
    )
    val inputs = sources.map { snapshot =>
      new StreamInputSource(
        () => InputStreamReader(ByteArrayInputStream(snapshot.bytes), UTF_8),
        snapshot.source.path.toString
      )
    }
    val compiled = OnionCompiler(config).compileDetailed(inputs)
    DiagnosticRenderer.printDiagnostics(compiled.diagnostics, err)

    if compiled.hasErrors then
      Left(ProjectError("Project compilation failed"))
    else
      for
        units <- ProjectBuilder.requireParsedUnits(compiled.debugArtifacts.parsedUnits)
        entryPoints <- EntryPointDiscovery.discover(paths.root, units)
        classNames <- writeClasses(compiled.classes)
        _ <-
          if classNames.nonEmpty then Right(())
          else Left(ProjectError(
            "Internal project error: successful compilation returned no classes"
          ))
        state = BuildState(
          BuildFingerprint.SchemaVersion,
          fingerprint,
          classNames,
          entryPoints.distinct.sortBy(entryPoint =>
            (entryPoint.source, entryPoint.line, entryPoint.column, entryPoint.className)
          )
        )
        _ <- BuildState.write(staging.resolve("build-state.json"), state)
        _ <- BuildOutputTransaction.promote(paths, staging, mover)
      yield ProjectBuild(paths, manifest, layout, state, cached = false)

  private def writeClasses(
    classes: Seq[onion.compiler.CompiledClass]
  ): Either[ProjectError, Vector[String]] =
    CompiledClassWriter.writeAll(classes)
      .left.map(error => ProjectError(error.message, error.cause))
      .map(_ => classes.iterator.map(_.className).toVector.distinct.sorted)

  private def readSources(
    sources: Vector[ProjectSource]
  ): Either[ProjectError, Vector[SourceSnapshot]] =
    try
      Right(sources.map(source => SourceSnapshot(source, Files.readAllBytes(source.path))))
    catch
      case NonFatal(error) =>
        Left(ProjectError(
          s"Could not read project source: ${describe(error)}",
          Some(error)
        ))

  private def validatePaths(paths: ProjectPaths): Either[ProjectError, Unit] =
    try
      val root = paths.root.toAbsolutePath.normalize
      val canonicalRoot = root.toRealPath()
      val target = paths.target.toAbsolutePath.normalize
      val onionState = paths.onionState.toAbsolutePath.normalize
      val classes = paths.classes.toAbsolutePath.normalize
      val buildState = paths.buildState.toAbsolutePath.normalize
      if canonicalRoot != root then
        Left(ProjectError(s"Project root is not canonical: $root"))
      else if target != root.resolve("target") ||
        classes != target.resolve("classes") ||
        onionState != target.resolve(".onion") ||
        buildState != onionState.resolve("build-state.json")
      then
        Left(ProjectError("Project output paths do not belong to the canonical project root"))
      else if Files.exists(target, NOFOLLOW_LINKS) &&
        (Files.isSymbolicLink(target) || !Files.isDirectory(target, NOFOLLOW_LINKS))
      then
        Left(ProjectError(s"Project target is not a real directory: $target"))
      else if Files.exists(onionState, NOFOLLOW_LINKS) &&
        (Files.isSymbolicLink(onionState) || !Files.isDirectory(onionState, NOFOLLOW_LINKS))
      then
        Left(ProjectError(s"Project state path is not a real directory: $onionState"))
      else if Files.isSymbolicLink(classes) then
        Left(ProjectError(s"Project classes path is a symbolic link: $classes"))
      else if Files.isSymbolicLink(buildState) then
        Left(ProjectError(s"Project build-state path is a symbolic link: $buildState"))
      else
        Right(())
    catch
      case NonFatal(error) =>
        Left(ProjectError(
          s"Could not validate project output paths: ${describe(error)}",
          Some(error)
        ))

  private def createStaging(paths: ProjectPaths): Either[ProjectError, Path] =
    var staging: Option[Path] = None
    try
      createRealDirectory(paths.target, paths.root, "project target")
      createRealDirectory(paths.onionState, paths.target, "project state directory")
      val created = ProjectTemporaryDirectory.create(
        paths.onionState,
        "staging-",
        paths.target
      )
      staging = Some(created)
      Files.createDirectory(created.resolve("classes"))
      Right(created)
    catch
      case NonFatal(error) =>
        staging.foreach(root =>
          try FileTree.delete(root, paths.target)
          catch case NonFatal(cleanup) => error.addSuppressed(cleanup)
        )
        Left(ProjectError(
          s"Could not create build staging directory: ${describe(error)}",
          Some(error)
        ))

  private def createRealDirectory(
    path: Path,
    parent: Path,
    description: String
  ): Unit =
    val normalized = path.toAbsolutePath.normalize
    val canonicalParent = parent.toAbsolutePath.normalize.toRealPath()
    if normalized.getParent != canonicalParent then
      throw IOException(s"$description is outside its canonical parent: $normalized")
    if Files.exists(normalized, NOFOLLOW_LINKS) then
      if Files.isSymbolicLink(normalized) ||
        !Files.isDirectory(normalized, NOFOLLOW_LINKS)
      then
        throw IOException(s"$description is not a real directory: $normalized")
    else
      Files.createDirectory(normalized)
    if normalized.toRealPath() != normalized then
      throw IOException(s"$description is not canonical: $normalized")

  private def finishStaging[A](
    result: Either[ProjectError, A],
    staging: Path,
    trustedBoundary: Path
  ): Either[ProjectError, A] =
    val cleanupFailure =
      try
        FileTree.delete(staging, trustedBoundary)
        None
      catch
        case NonFatal(error) => Some(error)

    cleanupFailure match
      case None => result
      case Some(cleanup) =>
        result match
          case Right(_) =>
            Left(ProjectError(
              s"Could not clean build staging directory: ${describe(cleanup)}",
              Some(cleanup)
            ))
          case Left(error) =>
            error.cause match
              case Some(primary) => primary.addSuppressed(cleanup)
              case None =>
                val primary = IOException(error.message)
                primary.addSuppressed(cleanup)
                return Left(error.copy(cause = Some(primary)))
            Left(error)

  private def describe(error: Throwable): String =
    Option(error.getMessage).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)
