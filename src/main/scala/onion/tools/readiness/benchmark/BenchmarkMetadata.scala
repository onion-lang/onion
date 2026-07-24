package onion.tools.readiness.benchmark

import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Path

final case class GitMetadata(commit: String, dirty: Boolean)

object GitMetadata:
  def capture(repoRoot: Path): Either[String, GitMetadata] =
    for
      commit <- command(repoRoot, "git", "rev-parse", "HEAD")
      status <- command(repoRoot, "git", "status", "--porcelain")
    yield GitMetadata(commit.trim, status.trim.nonEmpty)

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
  processors: Int,
  maxHeapBytes: Long,
  garbageCollectors: Vector[String],
  jvmArguments: Vector[String]
)

object EnvironmentMetadata:
  def capture(): EnvironmentMetadata =
    import scala.jdk.CollectionConverters.*
    EnvironmentMetadata(
      javaVendor = System.getProperty("java.vendor"),
      javaVersion = System.getProperty("java.version"),
      osName = System.getProperty("os.name"),
      osArch = System.getProperty("os.arch"),
      processors = Runtime.getRuntime.availableProcessors(),
      maxHeapBytes = Runtime.getRuntime.maxMemory(),
      garbageCollectors =
        ManagementFactory.getGarbageCollectorMXBeans.asScala
          .map(_.getName)
          .toVector,
      jvmArguments =
        ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.toVector
    )
