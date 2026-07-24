package onion.tools

import onion.tools.readiness.benchmark.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.Executors

object BenchmarkRunner:
  def main(args: Array[String]): Unit =
    BenchmarkOptions.parse(args) match
      case Left(message) =>
        Console.err.println(s"benchmark: $message")
        sys.exit(2)
      case Right(options) =>
        val repoRoot = Paths.get("").toAbsolutePath.normalize()
        val report = buildReport(repoRoot, options)
        writeJson(options.output, BenchmarkRender.json(report))
        options.stdoutFormat match
          case BenchmarkOutputFormat.Text =>
            println(BenchmarkRender.text(report))
          case BenchmarkOutputFormat.Json =>
            println(BenchmarkRender.json(report))
        if !report.succeeded then sys.exit(1)

  private[onion] def buildReport(
    repoRoot: Path,
    options: BenchmarkOptions
  ): PerformanceBenchmarkReport =
    val (git, metadataFailures) =
      GitMetadata.capture(repoRoot) match
        case Right(value) => (value, Vector.empty)
        case Left(message) =>
          (
            GitMetadata("unknown", dirty = true),
            Vector(
              BenchmarkFailure(
                FailureCategory.InvalidMeasurement,
                message
              )
            )
          )
    val executor = Executors.newSingleThreadExecutor()
    val engine = new BenchmarkEngine(
      options.runConfig,
      NanoClock.System,
      executor
    )
    val results =
      try CompileScenarioCatalog.default(repoRoot).map(engine.run)
      finally engine.close()
    PerformanceBenchmarkReport.create(
      git = git,
      environment = EnvironmentMetadata.capture(),
      runConfig = options.runConfig,
      scenarios = results,
      failures = metadataFailures
    )

  private def writeJson(path: Path, content: String): Unit =
    val parent = path.toAbsolutePath.normalize().getParent
    if parent != null then Files.createDirectories(parent)
    Files.writeString(path, content, StandardCharsets.UTF_8)
