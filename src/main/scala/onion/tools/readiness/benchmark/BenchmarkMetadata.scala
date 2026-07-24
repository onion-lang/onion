package onion.tools.readiness.benchmark

import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

final case class GitMetadata(commit: String, dirty: Boolean)

object GitMetadata:
  def capture(repoRoot: Path): Either[String, GitMetadata] =
    try
      for
        commit <- command(repoRoot, "git", "rev-parse", "HEAD")
        status <- command(repoRoot, "git", "status", "--porcelain")
      yield GitMetadata(commit.trim, status.trim.nonEmpty)
    catch
      case NonFatal(error) =>
        Left(
          Option(error.getMessage)
            .filter(_.nonEmpty)
            .getOrElse(error.getClass.getSimpleName)
        )

  private def command(repoRoot: Path, args: String*): Either[String, String] =
    val process =
      new ProcessBuilder(args*)
        .directory(repoRoot.toFile)
        .redirectErrorStream(true)
        .start()
    val output =
      try new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
      finally process.getInputStream.close()
    val exit = process.waitFor()
    if exit == 0 then Right(output)
    else Left(s"${args.mkString(" ")} failed with exit $exit: ${output.trim}")

final case class EnvironmentMetadata(
  javaVendor: String,
  javaVersion: String,
  osName: String,
  osArch: String,
  osReleaseId: String = "unknown",
  osReleaseVersion: String = "unknown",
  processors: Int,
  totalMemoryBytes: Long = 0L,
  maxHeapBytes: Long,
  garbageCollectors: Vector[String],
  jvmArguments: Vector[String]
)

object EnvironmentMetadata:
  def capture(): EnvironmentMetadata =
    val (osReleaseId, osReleaseVersion) = osRelease()
    EnvironmentMetadata(
      javaVendor = System.getProperty("java.vendor"),
      javaVersion = System.getProperty("java.version"),
      osName = System.getProperty("os.name"),
      osArch = System.getProperty("os.arch"),
      osReleaseId = osReleaseId,
      osReleaseVersion = osReleaseVersion,
      processors = Runtime.getRuntime.availableProcessors(),
      totalMemoryBytes = totalMemoryBytes(),
      maxHeapBytes = Runtime.getRuntime.maxMemory(),
      garbageCollectors =
        ManagementFactory.getGarbageCollectorMXBeans.asScala
          .map(_.getName)
          .toVector,
      jvmArguments =
        ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.toVector
    )

  private[benchmark] def parseOsRelease(
    lines: Seq[String]
  ): (String, String) =
    val values = lines.flatMap { line =>
      line.split("=", 2) match
        case Array(key, value) =>
          Some(key -> value.stripPrefix("\"").stripSuffix("\""))
        case _ => None
    }.toMap
    (
      values.getOrElse("ID", "unknown"),
      values.getOrElse("VERSION_ID", "unknown")
    )

  private def osRelease(): (String, String) =
    val release = Paths.get("/etc/os-release")
    if Files.isRegularFile(release) then
      parseOsRelease(Files.readAllLines(release).asScala.toSeq)
    else ("unknown", "unknown")

  private def totalMemoryBytes(): Long =
    ManagementFactory.getOperatingSystemMXBean match
      case bean: com.sun.management.OperatingSystemMXBean =>
        bean.getTotalMemorySize
      case _ => Runtime.getRuntime.maxMemory()
