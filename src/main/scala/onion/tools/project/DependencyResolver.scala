package onion.tools.project

import java.io.PrintStream
import java.nio.file.Path

import scala.jdk.CollectionConverters.*

import coursierapi.{Cache, Fetch, Logger}
import coursierapi.error.CoursierError

/**
 * The outcome of resolving a manifest's `[dependencies]`.
 *
 * @param coordinates the whole transitive set as `group:artifact:version`, sorted. This is
 *                    what goes in the lock file and into the build fingerprint — the direct
 *                    dependencies alone are not enough, because a transitive version can
 *                    change without `onion.toml` changing a byte.
 * @param classpath   the jars, in resolution order.
 */
final case class ResolvedDependencies(coordinates: Seq[String], classpath: Seq[Path]):
  def isEmpty: Boolean = classpath.isEmpty

object ResolvedDependencies:
  val empty: ResolvedDependencies = ResolvedDependencies(Seq.empty, Seq.empty)

/**
 * Resolves Maven coordinates to a classpath, through coursier.
 *
 * Before this existed, `ProjectBuilder` compiled every project with an empty classpath and
 * `onion.toml` rejected any key outside `[package]`, so a project could not reference a
 * single third-party jar — while the README described Onion as a language that "runs on the
 * JVM and calls Java directly". Scripts could always take `-classpath`; projects could not.
 *
 * Resolution is not a compiler concern, so failures come back as `ProjectError` with
 * coursier's own message, which already names the coordinate it could not find.
 */
object DependencyResolver:

  /**
   * @param progress where to draw download progress. Fetching a driver jar for the first
   *                 time can take a while and silence looks like a hang, so this is on by
   *                 default; pass `None` when the caller owns the terminal (the LSP does).
   */
  def resolve(
    dependencies: Seq[Dependency],
    repositories: Seq[String] = Seq.empty,
    progress: Option[PrintStream] = None
  ): Either[ProjectError, ResolvedDependencies] =
    if dependencies.isEmpty then Right(ResolvedDependencies.empty)
    else
      val cache = progress match
        case Some(stream) => Cache.create().withLogger(Logger.progressBars(stream))
        case None         => Cache.create().withLogger(Logger.nop())

      // Declared repositories go *before* the defaults, not after: an internal mirror
      // should win over Maven Central, and a coordinate that only exists internally
      // should not cost a public round trip (and a failed one) to discover that. The
      // defaults are kept rather than replaced, because a project that names a private
      // repository almost always still wants the public artifacts too.
      val base = Fetch.create().withCache(cache)
      val declared = repositories.map(coursierapi.MavenRepository.of(_))
      val withRepositories =
        if declared.isEmpty then base
        else base.withRepositories((declared ++ base.getRepositories.asScala)*)
      val fetch = dependencies.foldLeft(withRepositories) { (acc, dependency) =>
        acc.addDependencies(
          coursierapi.Dependency.of(dependency.group, dependency.artifact, dependency.version))
      }

      try
        val result = fetch.fetchResult()
        val coordinates = result.getDependencies.asScala.toSeq.map { resolved =>
          val module = resolved.getModule
          s"${module.getOrganization}:${module.getName}:${resolved.getVersion}"
        }.distinct.sorted
        Right(ResolvedDependencies(
          coordinates = coordinates,
          classpath = result.getFiles.asScala.toSeq.map(_.toPath)
        ))
      catch
        case error: CoursierError =>
          Left(ProjectError(s"Could not resolve dependencies: ${error.getMessage}", Some(error)))
